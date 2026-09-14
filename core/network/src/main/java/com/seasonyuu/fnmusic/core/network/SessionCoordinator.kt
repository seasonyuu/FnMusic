package com.seasonyuu.fnmusic.core.network

import com.seasonyuu.fnmusic.core.model.ConnectionProfile
import com.seasonyuu.fnmusic.core.model.Endpoint
import com.seasonyuu.fnmusic.core.model.LoginForm
import com.seasonyuu.fnmusic.core.model.MusicError
import com.seasonyuu.fnmusic.core.model.SessionRepository
import com.seasonyuu.fnmusic.core.model.SessionState
import com.seasonyuu.fnmusic.core.model.MusicUser
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Arrays
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionCoordinator(
    private val network: NetworkRuntime,
    private val vault: SessionVault,
    private val policy: ConnectionPolicy = ConnectionPolicy(),
) : SessionRepository {
    // Keep the first frame in a restoring state. Reconnect is launched from the
    // ViewModel asynchronously, so Unresolved here would incorrectly flash the
    // login form before encrypted credentials have been checked.
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Restoring)
    override val state: StateFlow<SessionState> = mutableState.asStateFlow()
    private val mutableLoginForm = MutableStateFlow(vault.loadLoginForm() ?: vault.load()?.let {
        LoginForm(
            useDirectConnection = it.profile.endpoint is Endpoint.Direct,
            fnId = (it.profile.endpoint as? Endpoint.FnConnect)?.fnId.orEmpty(),
            directUrl = (it.profile.endpoint as? Endpoint.Direct)?.baseUrl.orEmpty(),
            username = it.profile.username,
            allowPrivateLanHttp = it.profile.allowPrivateLanHttp,
        ).also(vault::saveLoginForm)
    } ?: LoginForm())
    val loginForm: StateFlow<LoginForm> = mutableLoginForm.asStateFlow()

    fun updateLoginForm(form: LoginForm) {
        vault.saveLoginForm(form)
        mutableLoginForm.value = form
    }

    private val connectionMutex = Mutex()

    init {
        network.installSessionRecovery { runBlocking { recoverForRequest() } }
    }

    override suspend fun connect(profile: ConnectionProfile, password: CharArray) {
        val passwordHash = try {
            val plainPassword = password.concatToString()
            updateLoginForm(loginForm.value.copy(
                useDirectConnection = profile.endpoint is Endpoint.Direct,
                fnId = (profile.endpoint as? Endpoint.FnConnect)?.fnId ?: loginForm.value.fnId,
                directUrl = (profile.endpoint as? Endpoint.Direct)?.baseUrl ?: loginForm.value.directUrl,
                username = profile.username,
                password = plainPassword,
                allowPrivateLanHttp = profile.allowPrivateLanHttp,
            ))
            plainPassword.sha256()
        } finally {
            Arrays.fill(password, '\u0000')
        }
        connectWithHash(profile, passwordHash, null)
    }

    override suspend fun reconnect() {
        mutableState.value = SessionState.Restoring
        val saved = vault.load() ?: run {
            mutableState.value = SessionState.Unresolved
            return
        }
        connectWithHash(saved.profile, saved.passwordHash, saved.token)
    }

    private suspend fun connectWithHash(profile: ConnectionProfile, passwordHash: String, existingToken: String?) {
        try {
            connectionMutex.withLock { establish(profile, passwordHash, existingToken) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            mutableState.value = SessionState.Error(error.userFacingMessage())
        }
    }

    private suspend fun recoverForRequest(): Boolean = connectionMutex.withLock {
        val saved = vault.load() ?: return@withLock false
        try {
            establish(saved.profile, saved.passwordHash, saved.token)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            mutableState.value = SessionState.Error(error.userFacingMessage())
            false
        }
    }

    private suspend fun establish(profile: ConnectionProfile, passwordHash: String, existingToken: String?) {
        mutableState.value = SessionState.Locating
        network.cookieJar.clear()
        network.activateBaseUrl("https://music.invalid/".toHttpUrl(), allowPrivateLanHttp = false)
        val baseUrl = when (val endpoint = profile.endpoint) {
            is Endpoint.FnConnect -> {
                val resolved = network.resolver.resolveAndActivate(endpoint.fnId)
                mutableState.value = SessionState.RelaySelected
                mutableState.value = SessionState.RelayActive
                resolved
            }
            is Endpoint.Direct -> policy.validateAndNormalize(endpoint.baseUrl, profile.allowPrivateLanHttp)
        }
        network.activateBaseUrl(baseUrl, profile.allowPrivateLanHttp)

        val reusableToken = existingToken?.takeIf(String::isNotBlank)
        val restoredSession = reusableToken?.let { token ->
            network.cookieJar.setMusicToken(baseUrl, token)
            val user = runCatching { network.sessionApi.currentUser().requireData() }.getOrNull()
            if (user != null) {
                runCatching { Triple(token, network.sessionApi.systemConfig().requireData(), user.toMusicUser()) }.getOrNull()
            } else null
        }
        if (restoredSession != null) {
            val (validatedToken, existingConfig, user) = restoredSession
            vault.save(SavedCredentials(profile, passwordHash, validatedToken))
            mutableState.value = SessionState.Ready(profile, existingConfig.serverName, existingConfig.serverVersion, user)
            return
        }

        val login = authenticate(profile, passwordHash, baseUrl)
        val token = login.userToken
        val config = network.sessionApi.systemConfig().requireData()
        vault.save(SavedCredentials(profile, passwordHash, token))
        mutableState.value = SessionState.Ready(profile, config.serverName, config.serverVersion,
            login.user?.toMusicUser())
    }

    private suspend fun authenticate(profile: ConnectionProfile, passwordHash: String, baseUrl: HttpUrl): LoginData {
        val login = network.sessionApi.passwordLogin(LoginRequest(profile.username, passwordHash, vault.deviceId())).requireData()
        network.cookieJar.setMusicToken(baseUrl, login.userToken)
        mutableState.value = SessionState.MusicAuthenticated
        return login
    }

    suspend fun currentUser(): MusicUser = network.sessionApi.currentUser().requireData().toMusicUser()

    suspend fun changePassword(password: CharArray) = connectionMutex.withLock {
        check(mutableState.value is SessionState.Ready) { "请先登录音乐账户" }
        try {
            require(password.isNotEmpty()) { "请输入新密码" }
            network.accountMutationApi.changePassword(ChangePasswordRequest(password.concatToString().sha256())).requireSuccess()
            clearChangedPasswordSession()
        } finally {
            Arrays.fill(password, '\u0000')
        }
    }

    fun updateServerName(name: String) {
        val ready = mutableState.value as? SessionState.Ready ?: return
        mutableState.value = ready.copy(serverName = name)
    }

    suspend fun updateUsername(username: String) = connectionMutex.withLock {
        val ready = mutableState.value as? SessionState.Ready ?: return@withLock
        val profile = ready.profile.copy(username = username)
        vault.load()?.let { vault.save(it.copy(profile = profile)) }
        updateLoginForm(loginForm.value.copy(username = username))
        mutableState.value = ready.copy(profile = profile, user = ready.user?.copy(name = username))
    }

    suspend fun finishPasswordChange(username: String? = null) = connectionMutex.withLock { clearChangedPasswordSession(username) }

    private fun clearChangedPasswordSession(username: String? = null) {
        vault.clearCredentials()
        updateLoginForm(loginForm.value.copy(password = "", username = username ?: loginForm.value.username))
        network.cookieJar.clear()
        mutableState.value = SessionState.Unresolved
    }

    override suspend fun logout(clearCredentials: Boolean) = connectionMutex.withLock {
        network.cookieJar.clear()
        if (clearCredentials) vault.clearCredentials()
        mutableState.value = SessionState.Unresolved
    }

    private fun Throwable.userFacingMessage(): String = when (this) {
        is MusicError -> message ?: "连接失败"
        is IllegalArgumentException -> message ?: "输入无效"
        else -> "连接失败，请检查地址、网络和账号"
    }
}

internal fun JsonObject.toMusicUser(): MusicUser = MusicUser(
    id = (get("guid") as? JsonPrimitive)?.contentOrNull.orEmpty(),
    name = (get("name") as? JsonPrimitive)?.contentOrNull.orEmpty(),
    role = (get("role") as? JsonPrimitive)?.contentOrNull,
    lastAccessedAt = (get("lastAccessedAt") as? JsonPrimitive)?.contentOrNull,
)
