package com.seasonyuu.fnmusic

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Display
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seasonyuu.fnmusic.car.CarProjectionService
import com.seasonyuu.fnmusic.car.ICarProjectionService
import com.seasonyuu.fnmusic.car.ProjectionDisplays
import com.seasonyuu.fnmusic.core.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@HiltViewModel
class CarProjectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel(), CarProjectionActions {
    private val manager = context.getSystemService(DisplayManager::class.java)
    private val mutableState = MutableStateFlow(CarProjectionState())
    override val state = mutableState.asStateFlow()
    private val handler = Handler(Looper.getMainLooper())
    private val displayRegistry = ProjectionDisplays()
    private val binderReceived = Shizuku.OnBinderReceivedListener { handler.post { updateEnvironment() } }
    private val binderDead = Shizuku.OnBinderDeadListener { handler.post { updateEnvironment() } }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == PERMISSION_REQUEST) handler.post {
            updateEnvironment()
            mutableState.update {
                it.copy(message = if (result == PackageManager.PERMISSION_GRANTED) null
                    else "尚未获得 Shizuku 授权，请在 Shizuku 中允许 FnMusic")
            }
        }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = updateEnvironment()
        override fun onDisplayRemoved(displayId: Int) {
            displayRegistry.removed(displayId)
            updateEnvironment()
        }
        override fun onDisplayChanged(displayId: Int) = updateEnvironment()
    }
    private val serviceArgs = Shizuku.UserServiceArgs(ComponentName(context, CarProjectionService::class.java))
        .daemon(false).processNameSuffix("car_projection").tag("car_projection")
        .version(BuildConfig.VERSION_CODE).debuggable(BuildConfig.DEBUG)

    init {
        manager.registerDisplayListener(displayListener, handler)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        updateEnvironment()
    }

    override fun refresh() {
        mutableState.update { if (it.busy) it else it.copy(message = null, launched = false) }
        updateEnvironment()
    }

    private fun updateEnvironment() {
        val permission = try {
            when {
                !Shizuku.pingBinder() -> ProjectionPermission.Unavailable
                Shizuku.isPreV11() || Shizuku.getVersion() < 13 -> ProjectionPermission.Unsupported
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ProjectionPermission.Granted
                else -> ProjectionPermission.Required
            }
        } catch (_: Exception) { ProjectionPermission.Unavailable }
        val displays = displayRegistry.update(manager.displays.filter { it.displayId != Display.DEFAULT_DISPLAY && it.isValid }
            .map { display ->
                val mode = display.mode
                ProjectionDisplay(
                    display.displayId, display.name, 0,
                    mode.physicalWidth, mode.physicalHeight,
                    display.state != Display.STATE_OFF,
                )
            })
        mutableState.update {
            it.copy(
                permission = permission,
                displays = displays,
                message = if (permission == it.permission) it.message else null,
            )
        }
    }

    override fun authorize() {
        updateEnvironment()
        if (state.value.permission != ProjectionPermission.Required) return
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                mutableState.update { it.copy(message = "请打开 Shizuku，在已授权应用中允许 FnMusic 后返回") }
            } else {
                Shizuku.requestPermission(PERMISSION_REQUEST)
            }
        } catch (_: Exception) {
            mutableState.update { it.copy(message = "无法请求授权，请打开 Shizuku 确认服务已运行") }
        }
    }

    override fun project(display: ProjectionDisplay) {
        if (state.value.busy) return
        updateEnvironment()
        if (state.value.permission != ProjectionPermission.Granted) {
            mutableState.update { it.copy(message = "请先启动并授权 Shizuku") }
            return
        }
        if (!isCurrent(display)) {
            mutableState.update { it.copy(message = "目标屏幕已断开或关闭，请刷新后重新选择") }
            return
        }
        mutableState.update { it.copy(busy = true, message = null, launched = false) }
        viewModelScope.launch {
            try {
                val error = withProjectionService { service ->
                    if (!isCurrent(display)) "目标屏幕已断开或关闭，请重新选择"
                    else withContext(Dispatchers.IO) { service.launch(display.id) }
                }
                mutableState.update { it.copy(launched = error == null, message = error) }
            } catch (_: TimeoutCancellationException) {
                mutableState.update { it.copy(message = "连接投屏服务超时，请打开 Shizuku 检查状态后重试") }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(message = "无法连接投屏服务，请确认 Shizuku 仍在运行后重试") }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    private fun isCurrent(target: ProjectionDisplay): Boolean {
        val current = manager.getDisplay(target.id) ?: return false
        return target.id != Display.DEFAULT_DISPLAY && current.isValid &&
            current.state != Display.STATE_OFF && current.name == target.name && displayRegistry.contains(target)
    }

    private suspend fun <T> withProjectionService(block: suspend (ICarProjectionService) -> T): T {
        var connection: ServiceConnection? = null
        try {
            val service = withTimeout(10_000) {
                suspendCancellableCoroutine { continuation ->
                    val client = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                            if (continuation.isActive) continuation.resume(ICarProjectionService.Stub.asInterface(binder))
                        }
                        override fun onServiceDisconnected(name: ComponentName) {
                            if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Shizuku disconnected"))
                        }
                    }
                    connection = client
                    Shizuku.bindUserService(serviceArgs, client)
                }
            }
            return block(service)
        } finally {
            connection?.let { client -> runCatching { Shizuku.unbindUserService(serviceArgs, client, true) } }
        }
    }

    override fun onCleared() {
        manager.unregisterDisplayListener(displayListener)
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
    }

    private companion object { const val PERMISSION_REQUEST = 7101 }
}
