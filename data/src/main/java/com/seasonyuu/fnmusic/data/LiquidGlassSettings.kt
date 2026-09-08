package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.LiquidGlassBlur
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Owns live preview separately from ordered persistence. Call public methods on the UI thread. */
class LiquidGlassSettings(
    scope: CoroutineScope,
    read: suspend () -> Float,
    private val write: suspend (Float) -> Unit,
) {
    data class State(val multiplier: Float = LiquidGlassBlur.Default, val error: String? = null)
    private data class Save(val revision: Long, val multiplier: Float)
    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState
    private var revision = 0L
    private val saves = Channel<Save>(Channel.UNLIMITED)

    init {
        scope.launch {
            try {
                val saved = read()
                if (revision == 0L) mutableState.value = State(LiquidGlassBlur.normalize(saved))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (revision == 0L) mutableState.value = State(error = "无法读取模糊设置，暂用默认值。")
            }
        }
        scope.launch {
            for (save in saves) {
                try {
                    write(save.multiplier)
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
        mutableState.value = State(LiquidGlassBlur.normalize(multiplier))
    }

    fun save() {
        saves.trySend(Save(revision, state.value.multiplier))
    }
}
