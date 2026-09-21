package com.seasonyuu.fnmusic.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlaylistEditingTest {
    @Test fun choosingDefaultAfterPhotoUploadsDefaultInsteadAndCleansDraft() = runBlocking {
        var uploads = 0
        var defaultUploads = 0
        var savedCover: String? = null
        var discarded: String? = null
        val controller = PlaylistEditorController(this, { "account" }, { playlist to tracks }, { _, _ -> PlaylistOrder() },
            { _, _, _ -> }, { _, _, cover -> savedCover = cover }, { _, _ -> }, {},
            prepareCover = { "photo" }, uploadCover = { uploads++; "uploaded" }, discardCover = { discarded = it },
            uploadDefaultCover = { assertEquals("playlist_default_3", it); defaultUploads++; "server-default-3" })
        controller.open(playlist.id)
        controller.state.first { it.draft != null }
        controller.selectPhoto("content://photo")
        controller.state.first { !it.preparingCover }
        controller.update(controller.state.value.draft!!.copy(usePhoto = false, coverId = "playlist_default_3"))
        controller.save()
        controller.state.first { !it.saving }
        assertTrue(controller.state.value.complete)
        assertEquals(0, uploads)
        assertEquals(1, defaultUploads)
        assertEquals("server-default-3", savedCover)
        controller.close()
        assertEquals("photo", discarded)
    }

    @Test fun defaultUploadIsDeferredAndRestoredRetryDoesNotUploadAgain() = runBlocking {
        var uploads = 0
        var edits = 0
        var failEdit = true
        var checkpoint: PlaylistEditDraft? = null
        fun controller() = PlaylistEditorController(this, { "account" }, { playlist to tracks }, { _, _ -> PlaylistOrder() },
            { _, _, _ -> }, { _, _, cover ->
                assertEquals("server-cover", cover)
                edits++
                if (failEdit) error("edit offline")
            }, { _, _ -> }, {}, restore = { checkpoint }, checkpoint = { checkpoint = it },
            uploadDefaultCover = { uploads++; assertEquals("playlist_default_4", it); "server-cover" })
        val first = controller()
        first.open(playlist.id)
        first.state.first { it.draft != null }
        first.update(first.state.value.draft!!.copy(coverId = "playlist_default_4"))
        assertEquals(0, uploads)
        first.close()
        assertEquals(0, uploads + edits)
        first.open(playlist.id)
        first.state.first { it.draft != null }
        first.update(first.state.value.draft!!.copy(coverId = "playlist_default_4"))
        first.save()
        first.state.first { !it.saving }
        assertEquals(1, uploads)
        assertTrue(first.state.value.error!!.contains("已完成：封面上传"))
        val second = controller()
        second.open(playlist.id)
        assertEquals("server-cover", second.state.value.draft!!.uploadedCoverId)
        failEdit = false
        second.save()
        second.state.first { !it.saving }
        assertTrue(second.state.value.complete)
        assertEquals(1, uploads)
        assertEquals(2, edits)
    }

    @Test fun failedDefaultUploadDoesNotEditAndCanRetryAnotherDefault() = runBlocking {
        var failUpload = true
        var edits = 0
        val templates = mutableListOf<String>()
        val controller = PlaylistEditorController(this, { "account" }, { playlist to tracks }, { _, _ -> PlaylistOrder() },
            { _, _, _ -> }, { _, _, cover -> assertEquals("server-cover", cover); edits++ }, { _, _ -> }, {},
            uploadDefaultCover = { templates += it; if (failUpload) error("upload offline"); "server-cover" })
        controller.open(playlist.id)
        controller.state.first { it.draft != null }
        controller.update(controller.state.value.draft!!.copy(coverId = "playlist_default_1"))
        controller.save()
        controller.state.first { !it.saving }
        assertEquals(0, edits)
        assertTrue(controller.state.value.error!!.contains("上传封面失败"))
        controller.update(controller.state.value.draft!!.copy(coverId = "playlist_default_2"))
        failUpload = false
        controller.save()
        controller.state.first { !it.saving }
        assertTrue(controller.state.value.complete)
        assertEquals(listOf("playlist_default_1", "playlist_default_2"), templates)
        assertEquals(1, edits)
    }

    @Test fun photoUploadsOnlyOnDoneAndRetryReusesSuccessfulUpload() = runBlocking {
        var uploads = 0
        var metadata = 0
        var failMetadata = true
        val discarded = mutableListOf<String>()
        val controller = PlaylistEditorController(this, { "account" }, { playlist to tracks }, { _, _ -> PlaylistOrder() },
            { _, _, _ -> }, { _, _, cover ->
                metadata++
                assertEquals("uploaded-cover", cover)
                if (failMetadata) error("offline")
            }, { _, _ -> }, {},
            prepareCover = { "draft-photo" }, uploadCover = { uploads++; "uploaded-cover" }, discardCover = discarded::add)
        controller.open(playlist.id)
        controller.state.first { it.draft != null }
        controller.selectPhoto("content://photo")
        controller.state.first { !it.preparingCover }
        assertTrue(controller.state.value.draft!!.dirty)
        assertEquals(0, uploads)
        controller.close()
        assertEquals(listOf("draft-photo"), discarded)
        assertEquals(0, metadata)
        controller.open(playlist.id)
        controller.state.first { it.draft != null }
        controller.selectPhoto("content://photo")
        controller.state.first { !it.preparingCover }
        controller.save()
        controller.state.first { !it.saving }
        assertEquals(1, uploads)
        assertTrue(controller.state.value.error!!.contains("已完成：封面上传"))
        failMetadata = false
        controller.save()
        controller.state.first { !it.saving }
        assertTrue(controller.state.value.complete)
        assertEquals(1, uploads)
        assertEquals(2, metadata)
        controller.close()
    }

    @Test fun invalidPhotoAndUploadFailureLeaveDraftRetryable() = runBlocking {
        var invalid = true
        var failUpload = true
        var uploads = 0
        val controller = PlaylistEditorController(this, { "account" }, { playlist to tracks }, { _, _ -> PlaylistOrder() },
            { _, _, _ -> }, { _, _, _ -> }, { _, _ -> }, {},
            prepareCover = { if (invalid) error("图片不能超过 5 MiB"); "photo" },
            uploadCover = { uploads++; if (failUpload) error("offline"); "uploaded" })
        controller.open(playlist.id)
        controller.state.first { it.draft != null }
        controller.selectPhoto("content://photo")
        controller.state.first { !it.preparingCover }
        assertFalse(controller.state.value.draft!!.dirty)
        assertTrue(controller.state.value.error!!.contains("5 MiB"))
        invalid = false
        controller.selectPhoto("content://photo")
        controller.state.first { !it.preparingCover }
        controller.save()
        controller.state.first { !it.saving }
        assertTrue(controller.state.value.error!!.contains("上传封面失败"))
        assertFalse(controller.state.value.draft!!.editingLocked)
        failUpload = false
        controller.save()
        controller.state.first { !it.saving }
        assertTrue(controller.state.value.complete)
        assertEquals(2, uploads)
    }

    @get:Rule val folder = TemporaryFolder()
    private val playlist = Playlist(PlaylistId("p"), "Original")
    private val tracks = listOf("a", "b", "a", "c").map { Track(TrackId(it), it) }

    @Test fun reconcilePreservesDuplicatesAndAppendsNewSongs() {
        val keys = tracks.playlistKeys()
        val order = PlaylistOrder(true, listOf(keys[2], keys[1], keys[0], keys[3]))
        assertEquals(listOf(keys[2], keys[1], keys[0], keys[3]), order.reconcile(tracks).keys)
        val changed = tracks.filterNot { it.id.value == "b" } + Track(TrackId("new"), "new")
        val merged = order.reconcile(changed)
        assertEquals(listOf("a", "a", "c", "new"), merged.apply(changed).map { it.id.value })
        assertEquals(keys[2], merged.keys.first())
        assertEquals(changed, merged.copy(enabled = false).apply(changed))
        assertEquals(merged.keys, merged.copy(enabled = false).copy(enabled = true).keys)
    }

    @Test fun localSettingsPersistAndAreIsolatedByAccountAndPlaylist() = runBlocking {
        val file = folder.newFolder().resolve("orders.preferences_pb")
        suspend fun open(block: suspend (PlaylistOrderStore) -> Unit) {
            val job = SupervisorJob()
            val store = PlaylistOrderStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file })
            try { block(store) } finally { job.cancelAndJoin() }
        }
        val order = PlaylistOrder(true, tracks.playlistKeys().reversed())
        open { store ->
            assertEquals(PlaylistOrder(), store.read("one", playlist.id))
            store.write("one", playlist.id, order)
        }
        open { store ->
            assertEquals(order, store.read("one", playlist.id))
            assertEquals(PlaylistOrder(), store.read("two", playlist.id))
            assertEquals(PlaylistOrder(), store.read("one", PlaylistId("other")))
            store.write("one", playlist.id, order.copy(enabled = false))
        }
        open { store ->
            assertEquals(order.copy(enabled = false), store.read("one", playlist.id))
            store.delete("one", playlist.id)
            assertEquals(PlaylistOrder(), store.read("one", playlist.id))
        }
    }

    @Test fun retryOnlyRunsUnfinishedStepsAndCancelDoesNotWrite() = runBlocking {
        var metadata = 0; var removed = 0; var writes = 0; var refreshes = 0
        var failWrite = true
        val controller = PlaylistEditorController(this, { "account" }, { playlist to tracks }, { _, _ -> PlaylistOrder() },
            { _, _, _ -> writes++; if (failWrite) error("disk full") },
            { _, _, _ -> metadata++ }, { _, ids -> assertEquals(listOf(TrackId("a")), ids); removed++ }, { refreshes++ })
        controller.open(playlist.id)
        controller.state.first { it.draft != null }
        val draft = controller.state.value.draft!!.copy(name = "Changed", selected = setOf(TrackId("a")))
        controller.update(draft.removeSelected())
        assertEquals(0, removed)
        controller.close()
        assertEquals(0, metadata + writes + removed)
        controller.open(playlist.id)
        controller.state.first { it.draft != null }
        controller.update(controller.state.value.draft!!.copy(name = "Changed", removed = setOf(TrackId("a"))))
        controller.save()
        controller.state.first { it.error != null }
        assertEquals(1, metadata); assertEquals(1, removed); assertEquals(0, refreshes)
        assertTrue(controller.state.value.error!!.contains("已完成"))
        failWrite = false
        controller.save()
        controller.state.first { it.complete }
        assertEquals(1, metadata); assertEquals(1, removed); assertEquals(2, writes); assertEquals(1, refreshes)
    }

    @Test fun orderingAloneNeverCallsServerMutationsAndFailedRefreshCanRetry() = runBlocking {
        var writes = 0; var refreshes = 0
        val controller = PlaylistEditorController(this, { "account" }, { playlist to tracks }, { _, _ -> PlaylistOrder() },
            { _, _, order -> assertTrue(order.enabled); writes++ },
            { _, _, _ -> error("unexpected metadata mutation") }, { _, _ -> error("unexpected removal") },
            { refreshes++; if (refreshes == 1) error("offline") })
        controller.open(playlist.id); controller.state.first { it.draft != null }
        val draft = controller.state.value.draft!!
        controller.update(draft.copy(order = draft.order.copy(enabled = true)).move(0, 3))
        controller.save(); controller.state.first { it.error != null }
        controller.save(); controller.state.first { it.complete }
        assertEquals(1, writes); assertEquals(2, refreshes)
    }

    @Test fun loadFailureNeverAllowsSavingOrOverwritesLocalOrder() = runBlocking {
        var writes = 0
        val controller = PlaylistEditorController(this, { "account" }, { error("page failed") }, { _, _ -> PlaylistOrder() },
            { _, _, _ -> writes++ }, { _, _, _ -> error("unexpected mutation") }, { _, _ -> error("unexpected mutation") }, {})
        controller.open(playlist.id); controller.state.first { it.error != null }
        controller.save()
        assertNull(controller.state.value.draft); assertEquals(0, writes)
    }
}
