package com.seasonyuu.fnmusic

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.aboutlibraries.Libs
import com.seasonyuu.fnmusic.core.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class AboutViewModel @Inject constructor(@ApplicationContext private val context: Context) : ViewModel(), AboutActions {
    override val info = AppAboutInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
        BuildConfig.DEBUG || BuildConfig.VERSION_NAME.contains("-dev."))
    private val repository = AppUpdateRepository()
    private val mutableUpdate = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    override val update = mutableUpdate.asStateFlow()
    private val mutableOpenSource = MutableStateFlow<OpenSourceState>(OpenSourceState.Loading)
    override val openSource = mutableOpenSource.asStateFlow()
    private var loadingLibraries = false

    override fun checkUpdate() {
        if (mutableUpdate.value == AppUpdateState.Checking) return
        mutableUpdate.value = AppUpdateState.Checking
        viewModelScope.launch {
            try {
                mutableUpdate.value = AppUpdateState.Available(repository.check(info.versionName))
            } catch (e: CancellationException) {
                mutableUpdate.value = AppUpdateState.Idle
                throw e
            } catch (e: java.net.SocketTimeoutException) {
                mutableUpdate.value = AppUpdateState.Failed("连接 GitHub 超时，请重试或打开发布列表")
            } catch (e: java.io.IOException) {
                mutableUpdate.value = AppUpdateState.Failed(when {
                    e.message?.startsWith("GitHub") == true || e.message?.startsWith("暂未") == true ||
                        e.message?.startsWith("检查更新失败") == true -> e.message!!
                    else -> "无法连接 GitHub，请检查网络后重试或打开发布列表"
                })
            } catch (_: Exception) {
                mutableUpdate.value = AppUpdateState.Failed("无法读取版本信息，请重试或打开发布列表")
            }
        }
    }

    override fun loadLibraries() {
        if (loadingLibraries || mutableOpenSource.value is OpenSourceState.Ready) return
        loadingLibraries = true
        mutableOpenSource.value = OpenSourceState.Loading
        viewModelScope.launch {
            try {
                val libraries = withContext(Dispatchers.IO) {
                    val json = context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
                    Libs.Builder().withJson(json).build().libraries.map { library ->
                        OpenSourceLibrary(
                            id = library.uniqueId,
                            name = library.name,
                            version = library.artifactVersion.orEmpty(),
                            authors = library.developers.mapNotNull { it.name }.filter { it.isNotBlank() }
                                .joinToString("、").ifBlank { library.organization?.name.orEmpty() },
                            website = library.website?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
                            licenses = library.licenses.map { OpenSourceLicense(it.name, it.licenseContent.orEmpty()) },
                            description = library.description.orEmpty(),
                        )
                    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                }
                mutableOpenSource.value = OpenSourceState.Ready(libraries)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableOpenSource.value = OpenSourceState.Failed
            } finally {
                loadingLibraries = false
            }
        }
    }
}
