package com.seasonyuu.fnmusic.feature.music

import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LibraryAdministrationStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val api = Stub()
    private var changes = 0
    private val state = LibraryAdministrationState(api, scope, onCatalogChanged = { changes++ })
    @After fun cleanup() { scope.cancel() }

    @Test fun selectionMatchesWebDepthAndOnlyBlocksExactDuplicates() {
        assertFalse(selectableLibraryPath("/vol1", emptyList()))
        assertFalse(selectableLibraryPath("/vol1/1000", emptyList()))
        assertTrue(selectableLibraryPath("/vol1/1000/Music", emptyList()))
        assertTrue(selectableLibraryPath("/vol02/remote", emptyList()))
        assertTrue(selectableLibraryPath("/vol00/usb", emptyList()))
        assertFalse(selectableLibraryPath("/vol1/1000/Music/", listOf("/vol1/1000/Music")))
        assertTrue(selectableLibraryPath("/vol1/1000/Music/Album", listOf("/vol1/1000/Music")))
        assertTrue(selectableLibraryPath("/vol1/1000/Music", listOf("/vol1/1000/Music/Album")))
    }
    @Test fun creationUsesWebDefaultsAndEnforcesFolderLimit() = runBlocking {
        state.refresh(); state.create()
        assertTrue(state.draft!!.lyrics)
        assertEquals("cloud_preferred", state.draft!!.metadata)
        assertTrue(state.pickerOpen)
        state.closeEditor()
        api.folderValues = List(200) { MusicFolder("$it") }; state.refresh(); state.create()
        assertNull(state.draft)
        assertNotNull(state.actionError)
    }
    @Test fun localOnlyDisablesLyricsOnWireAndFailureKeepsDraft() = runBlocking {
        state.refresh(); state.create()
        state.draft = state.draft!!.copy(path = "/vol1/1000/Music", metadata = "local_only")
        api.saveFailure = java.net.SocketTimeoutException()
        state.save()
        assertFalse(api.savedLyrics!!)
        assertTrue(state.draft!!.lyrics)
        assertTrue(state.editError!!.contains("待确认"))
        assertEquals(1, api.saves)
        api.saveFailure = null; state.save()
        assertNull(state.draft); assertEquals(1, changes); assertEquals(0, api.scans)
    }
    @Test fun editPreservesLegacyAndRejectsUnknownMetadata() {
        api.detail = MusicFolder("a", path = "/vol1/1000/Music", metadataPreference = "local_preferred")
        state.edit("a"); assertEquals("local_preferred", state.draft!!.metadata); assertTrue(state.draft!!.valid)
        api.detail = api.detail.copy(metadataPreference = "future_mode")
        state.edit("a"); assertEquals("future_mode", state.draft!!.metadata); assertFalse(state.draft!!.valid)
        state.save(); assertEquals(0, api.saves)
    }
    @Test fun directoryFailureIsNotEmptyAndOldRequestCannotOverwriteNewPath() = runBlocking {
        val slow = CompletableDeferred<List<MusicDirectory>>()
        api.children = { path -> if (path == "/slow") withContext(NonCancellable) { slow.await() } else listOf(MusicDirectory("/fast/child", "child")) }
        state.browse("/slow"); state.browse("/fast")
        slow.complete(listOf(MusicDirectory("/slow/child", "wrong")))
        assertEquals("/fast", state.directory); assertEquals("/fast/child", state.children.single().path)
        api.children = { throw IOException() }; state.browse("/failure")
        assertNotNull(state.directoryError); assertFalse(state.canSelectDirectory)
        api.children = { emptyList() }; state.browse("/vol1/1000/Music/Empty")
        assertNull(state.directoryError); assertTrue(state.children.isEmpty()); assertTrue(state.canSelectDirectory)
    }
    @Test fun browsingDoesNotCommitSelectionAndBackStopsAtAuthorizedRoot() {
        state.draft = LibraryDraft(path = "/vol1/1000/Original")
        state.browse(null); assertEquals(1, state.roots!!.size)
        state.browse("/vol1/1000/Music/Album")
        assertEquals("/vol1/1000/Original", state.draft!!.path)
        state.chooseDirectory(); assertEquals("/vol1/1000/Music/Album", state.draft!!.path)
        state.directoryUp(); assertEquals("/vol1/1000/Music", state.directory)
        state.directoryUp(); assertNull(state.directory)
    }
    @Test fun scanSubmissionWaitsForNewTaskAndCompletionInvalidatesCatalog() = runBlocking {
        state.refresh(); state.scan("a")
        assertTrue("a" in state.pendingScans); assertEquals(0, changes)
        api.taskValues = listOf(task(done = false)); state.refreshTasks()
        assertTrue(state.pendingScans.isEmpty()); assertEquals(0, changes)
        api.taskValues = listOf(task(done = true)); state.refreshTasks(); assertEquals(1, changes)
        state.refreshTasks(); assertEquals(1, changes)
    }
    @Test fun progressCannotClaimCompletionAndActionsRespectServerFlags() {
        val running = task(done = false).copy(total = 10, completed = 10)
        assertEquals(.99f, running.progress!!, .0001f)
        assertFalse(running.statusText.contains("已完成"))
        assertEquals("取消中", running.copy(cancelling = true).statusText)
        state.retry(running.copy(retryable = true)); assertEquals(0, api.retries)
        state.retry(task(done = true)); assertEquals(0, api.retries)
        state.retry(task(done = true).copy(retryable = true)); assertEquals(1, api.retries)
        state.cancel(running.copy(cancelling = true)); assertEquals(0, api.cancels)
        state.cancel(running); assertEquals(1, api.cancels)
        assertTrue(task(done = true).copy(failed = 2).statusText.contains("2 项失败"))
    }
    @Test fun failedTaskRefreshPreservesTasksAndFolderList() = runBlocking {
        api.taskValues = listOf(task(false)); state.refresh()
        api.taskFailure = IOException(); state.refresh()
        assertEquals(1, state.tasks.size); assertEquals(1, state.folders!!.size)
        assertNotNull(state.taskError); assertNull(state.listError)
    }
    @Test fun editorSnapshotRestoresDraftWithoutPersistingAuthorizedDirectories() = runBlocking {
        state.refresh(); state.edit("a")
        state.draft = state.draft!!.copy(path = "/vol1/1000/Music/New", metadata = "local_only", lyrics = true)
        val restored = LibraryAdministrationState(api, scope)
        restored.restoreEditorSnapshot(state.saveEditorSnapshot())
        assertEquals(state.draft, restored.draft)
        assertTrue(restored.draft!!.dirty)
        assertNull(restored.roots)
        assertNull(restored.folders)
        assertEquals(0, api.saves)
    }
    @Test fun noAuthorizationAndDuplicateSelectionNeverEnableConfirmation() = runBlocking {
        state.refresh(); state.create()
        state.browse("/vol1/1000/Existing")
        assertFalse(state.canSelectDirectory)
        state.browse("/vol2/1000/OutsideAuthorization")
        assertFalse(state.canSelectDirectory)
        state.draft = state.draft!!.copy(path = "/vol1/1000/Existing/")
        state.save(); assertEquals(0, api.saves); assertNotNull(state.editError)
        api.rootValues = emptyList(); state.browse(null)
        assertTrue(state.roots!!.isEmpty()); assertNull(state.directoryError); assertFalse(state.canSelectDirectory)
    }
    @Test fun unobservedSubmissionDoesNotLockScanningForeverOrClaimSuccess() = runBlocking {
        var now = 0L
        val state = LibraryAdministrationState(api, scope, monotonicMillis = { now })
        state.refresh(); state.scan("a")
        assertTrue("a" in state.pendingScans)
        now = 30_001; state.refreshTasks()
        assertTrue(state.pendingScans.isEmpty())
        assertTrue(state.actionError!!.contains("暂未发现"))
        assertEquals(1, api.scans)
    }
    private fun task(done: Boolean) = MusicScanTask("scan", "a", 0, 0, 0, done, false, id = "task-1")

    private class Stub : MusicAdministration {
        var folderValues = listOf(MusicFolder("a", path = "/vol1/1000/Existing"))
        var taskValues = emptyList<MusicScanTask>()
        var taskFailure: Exception? = null
        var detail = MusicFolder("a")
        var children: suspend (String) -> List<MusicDirectory> = { emptyList() }
        var saveFailure: Exception? = null
        var savedLyrics: Boolean? = null
        var saves = 0; var scans = 0; var retries = 0; var cancels = 0
        var rootValues = listOf(AuthorizedMusicDirectory("/vol1/1000/Music", 1))
        override suspend fun authorizedDirectories() = rootValues
        override suspend fun childDirectories(parent: String) = children(parent)
        override suspend fun folderDetail(guid: String) = detail
        override suspend fun scanAllFolders() { scans++ }
        override suspend fun cancelTask(taskId: String) { cancels++ }
        override suspend fun retryTask(taskId: String) { retries++ }
        override suspend fun rebuildSearchIndex() = SearchIndexResult(1, 2, 3)
        override suspend fun scanTasks(): List<MusicScanTask> { taskFailure?.let { throw it }; return taskValues }
        override suspend fun folders() = folderValues
        override suspend fun saveFolder(original: MusicFolder?, path: String, metadataPreference: String, autoDownloadLyric: Boolean) { saves++; savedLyrics = autoDownloadLyric; saveFailure?.let { throw it } }
        override suspend fun removeFolder(guid: String) {}
        override suspend fun scanFolder(guid: String) { scans++ }
        override suspend fun users() = emptyList<ManagedMusicUser>()
        override suspend fun saveUser(original: ManagedMusicUser?, username: String, password: String?, access: FolderAccess) {}
        override suspend fun defaultAccess() = FolderAccess()
        override suspend fun saveDefaultAccess(access: FolderAccess) {}
        override suspend fun serverSettings() = MusicServerSettings("Test")
        override suspend fun saveServerSettings(value: MusicServerSettings) {}
    }
}
