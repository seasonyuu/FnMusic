package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.LiquidGlassBlur
import com.seasonyuu.fnmusic.core.model.LiquidGlassPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Owns live preview separately from ordered persistence. Call public methods on the UI thread. */
class LiquidGlassSettings(
    scope: CoroutineScope,
    read: suspend () -> LiquidGlassPreference,
    private val write: suspend (LiquidGlassPreference) -> Unit,
) {
    data class State(
        val multiplier: Float = LiquidGlassBlur.Default,
        val enabled: Boolean = true,
        val error: String? = null,
    ) {
        val preference get() = LiquidGlassPreference(multiplier, enabled)
    }
    private data class Save(val revision: Long, val preference: LiquidGlassPreference)
    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState
    private var revision = 0L
    private val saves = Channel<Save>(Channel.UNLIMITED)

    init {
        scope.launch {
            try {
                val saved = read().normalized()
                if (revision == 0L) mutableState.value = State(saved.multiplier, saved.enabled)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (revision == 0L) mutableState.value = State(error = "无法读取 Liquid Glass 设置，暂用默认值。")
            }
        }
        scope.launch {
            for (save in saves) {
                try {
                    write(save.preference)
                    if (save.revision == revision) mutableState.value = mutableState.value.copy(error = null)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (save.revision == revision) {
                        mutableState.value = mutableState.value.copy(error = "保存失败，当前预览仍然有效，请重试。")
                    }
                }
            }
        }.invokeOnCompletion { saves.close() }
    }

    fun preview(multiplier: Float) {
        revision++
        mutableState.value = mutableState.value.copy(multiplier = LiquidGlassBlur.normalize(multiplier), error = null)
    }

    fun setEnabled(enabled: Boolean) {
        revision++
        mutableState.value = mutableState.value.copy(enabled = enabled, error = null)
        save()
    }

    fun save() {
        saves.trySend(Save(revision, state.value.preference))
    }
}
