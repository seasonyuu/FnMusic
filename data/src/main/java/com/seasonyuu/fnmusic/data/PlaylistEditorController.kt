package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Owns a single edit session across sheet recompositions and activity recreation. */
class PlaylistEditorController(
    private val scope: CoroutineScope,
    private val account: () -> String,
    private val load: suspend (PlaylistId) -> Pair<Playlist, List<Track>>,
    private val readOrder: suspend (String, PlaylistId) -> PlaylistOrder,
    private val writeOrder: suspend (String, PlaylistId, PlaylistOrder) -> Unit,
    private val updateMetadata: suspend (PlaylistId, String, String?) -> Unit,
    private val removeTracks: suspend (PlaylistId, List<TrackId>) -> Unit,
    private val refresh: suspend (PlaylistId) -> Unit,
    private val restore: () -> PlaylistEditDraft? = { null },
    private val checkpoint: (PlaylistEditDraft?) -> Unit = {},
    private val prepareCover: suspend (String) -> String = { error("不支持相册封面") },
    private val uploadCover: suspend (String) -> String = { error("不支持封面上传") },
    private val discardCover: (String) -> Unit = {},
    private val uploadDefaultCover: suspend (String) -> String = { error("不支持默认封面上传") },
) : PlaylistEditActions {
    private val mutable = MutableStateFlow(PlaylistEditorState())
    override val state: StateFlow<PlaylistEditorState> = mutable.asStateFlow()
    private var job: Job? = null

    override fun open(id: PlaylistId) {
        if (state.value.id == id && (state.value.loading || state.value.draft?.account == account())) return
        job?.cancel()
        val saved = restore()?.takeIf { it.original.id == id && it.account == account() }
        if (saved != null) {
            mutable.value = PlaylistEditorState(id = id, draft = saved)
            return
        }
        mutable.value = PlaylistEditorState(id = id)
        retryLoad()
    }

    override fun retryLoad() {
        val id = state.value.id ?: return
        if (state.value.loading || state.value.saving) return
        val owner = account()
        mutable.value = PlaylistEditorState(id = id, loading = true)
        job = scope.launch {
            try {
                val (playlist, tracks) = load(id)
                val order = readOrder(owner, id).reconcile(tracks)
                check(owner == account()) { "账号已切换，请重新打开歌单" }
                val draft = PlaylistEditDraft(owner, playlist, tracks, order)
                checkpoint(draft)
                mutable.value = PlaylistEditorState(id = id, draft = draft)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = PlaylistEditorState(id = id, error = e.message ?: "加载失败") }
        }
    }

    override fun update(draft: PlaylistEditDraft) {
        val current = state.value
        if (current.busy || current.complete || current.draft?.editingLocked == true ||
            draft.original.id != current.id || draft.account != account()) return
        val editable = draft.copy(metadataSaved = false, removalSaved = false, orderSaved = false)
        checkpoint(editable)
        mutable.value = current.copy(draft = editable, error = null)
    }

    override fun selectPhoto(uri: String) {
        val current = state.value
        val draft = current.draft ?: return
        if (current.busy || current.complete || draft.editingLocked) return
        mutable.value = current.copy(preparingCover = true, error = null)
        job = scope.launch {
            var prepared: String? = null
            try {
                // Retain the returned path even if the session is canceled during the copy,
                // so its private draft file can always be cleaned up.
                prepared = withContext(NonCancellable) { prepareCover(uri) }
                ensureActive()
                check(draft.account == account()) { "账号已切换，请重新打开歌单" }
                val next = draft.copy(photoPath = prepared, usePhoto = true, uploadedCoverId = null,
                    metadataSaved = false, removalSaved = false, orderSaved = false)
                checkpoint(next)
                mutable.value = current.copy(draft = next, preparingCover = false, error = null)
                draft.photoPath?.let(discardCover)
            } catch (e: CancellationException) { prepared?.let(discardCover); throw e }
            catch (e: Exception) {
                prepared?.let(discardCover)
                mutable.value = current.copy(preparingCover = false, error = "读取封面失败：${e.message ?: "请重试"}")
            }
        }
    }

    override fun save() {
        var draft = state.value.draft ?: return
        if (state.value.busy || state.value.complete || !draft.valid) return
        mutable.value = state.value.copy(saving = true, error = null)
        job = scope.launch {
            var stage = "更新名称与封面"
            fun record(next: PlaylistEditDraft) {
                draft = next
                checkpoint(next)
                mutable.value = state.value.copy(draft = next)
            }
            fun ensureAccount() { check(draft.account == account()) { "账号已切换，请重新打开歌单" } }
            try {
                ensureAccount()
                val defaultCover = draft.coverId?.takeIf { it in (1..4).map { index -> "playlist_default_$index" } }
                val needsDefaultUpload = !draft.usePhoto && defaultCover != null &&
                    (draft.coverId != draft.original.coverId || draft.name.trim() != draft.original.name)
                val needsCoverUpload = draft.usePhoto || needsDefaultUpload
                if (needsCoverUpload && draft.uploadedCoverId == null) {
                    stage = "上传封面"
                    val cover = if (draft.usePhoto) uploadCover(requireNotNull(draft.photoPath) { "请重新选择照片" })
                        else uploadDefaultCover(requireNotNull(defaultCover))
                    require(cover.isNotBlank()) { "上传响应缺少 coverId" }
                    record(draft.copy(uploadedCoverId = cover, completedSteps = draft.completedSteps + "封面上传"))
                }
                stage = "更新名称与封面"
                ensureAccount()
                if (!draft.metadataSaved) {
                    val cover = if (needsCoverUpload) requireNotNull(draft.uploadedCoverId) else draft.coverId
                    val changed = draft.name.trim() != draft.original.name || cover != draft.original.coverId
                    if (changed) updateMetadata(draft.original.id, draft.name.trim(), cover)
                    record(draft.copy(metadataSaved = true, completedSteps = draft.completedSteps + if (changed) listOf("名称与封面") else emptyList()))
                }
                stage = "移除歌曲"
                ensureAccount()
                if (!draft.removalSaved) {
                    if (draft.removed.isNotEmpty()) removeTracks(draft.original.id, draft.removed.toList())
                    record(draft.copy(removalSaved = true, completedSteps = draft.completedSteps + if (draft.removed.isNotEmpty()) listOf("歌曲移除") else emptyList()))
                }
                stage = "保存本地排序"
                ensureAccount()
                if (!draft.orderSaved) {
                    writeOrder(draft.account, draft.original.id, draft.order.reconcile(draft.remaining))
                    record(draft.copy(orderSaved = true, completedSteps = draft.completedSteps + "本地排序"))
                }
                stage = "刷新歌单"
                ensureAccount()
                refresh(draft.original.id)
                ensureAccount()
                checkpoint(null)
                mutable.value = state.value.copy(saving = false, complete = true)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val completed = draft.completedSteps.takeIf { it.isNotEmpty() }?.joinToString("、")?.let { "已完成：$it。" }.orEmpty()
                mutable.value = state.value.copy(saving = false, error = "$completed${stage}失败：${e.message ?: "请重试"}")
            }
        }
    }

    override fun close() {
        if (state.value.busy) return
        job?.cancel()
        state.value.draft?.photoPath?.let(discardCover)
        checkpoint(null)
        mutable.value = PlaylistEditorState()
    }
}
