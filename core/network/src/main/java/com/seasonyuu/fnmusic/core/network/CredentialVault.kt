package com.seasonyuu.fnmusic.core.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.seasonyuu.fnmusic.core.model.ConnectionProfile
import com.seasonyuu.fnmusic.core.model.LoginForm
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class SavedCredentials(
    val profile: ConnectionProfile,
    val passwordHash: String,
    val token: String,
)

interface SessionVault {
    fun deviceId(): String
    fun save(credentials: SavedCredentials)
    fun load(): SavedCredentials?
    fun saveLoginForm(form: LoginForm)
    fun loadLoginForm(): LoginForm?
    fun clearCredentials()
}

class CredentialVault(context: Context, private val json: Json) : SessionVault {
    private val preferences = context.getSharedPreferences("encrypted_session", Context.MODE_PRIVATE)

    override fun deviceId(): String = preferences.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().replace("-", "")
        .also { value -> preferences.edit { putString(KEY_DEVICE_ID, value) } }

    override fun save(credentials: SavedCredentials) {
        writeEncrypted(KEY_CREDENTIALS, json.encodeToString(credentials))
    }

    override fun load(): SavedCredentials? = readEncrypted(KEY_CREDENTIALS)?.let {
        runCatching { json.decodeFromString<SavedCredentials>(it) }.getOrNull()
    }

    override fun saveLoginForm(form: LoginForm) = writeEncrypted(KEY_LOGIN_FORM, json.encodeToString(form))

    override fun loadLoginForm(): LoginForm? = readEncrypted(KEY_LOGIN_FORM)?.let {
        runCatching { json.decodeFromString<LoginForm>(it) }.getOrNull()
    }

    private fun writeEncrypted(key: String, value: String) {
        val plain = value.encodeToByteArray()
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val combined = cipher.iv + cipher.doFinal(plain)
            preferences.edit { putString(key, Base64.encodeToString(combined, Base64.NO_WRAP)) }
        } finally {
            plain.fill(0)
        }
    }

    private fun readEncrypted(key: String): String? {
        val encoded = preferences.getString(key, null) ?: return null
        return runCatching {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            require(combined.size > IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, combined.copyOfRange(0, IV_SIZE)))
            val plain = cipher.doFinal(combined.copyOfRange(IV_SIZE, combined.size))
            try { plain.decodeToString() } finally { plain.fill(0) }
        }.getOrNull()
    }

    override fun clearCredentials() {
        preferences.edit { remove(KEY_CREDENTIALS) }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "fn_music_session_key"
        const val KEY_LOGIN_FORM = "login_form"
        const val KEY_CREDENTIALS = "credentials"
        const val KEY_DEVICE_ID = "device_id"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
