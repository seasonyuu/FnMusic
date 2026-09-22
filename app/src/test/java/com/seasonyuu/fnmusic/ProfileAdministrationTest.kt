package com.seasonyuu.fnmusic

import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ProfileAdministrationTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    @After fun close() { scope.cancel() }
    @Test fun onlyChangingOwnPasswordEndsTheSession() = runBlocking {
        var endedFor: String? = null
        var renamed = false
        val api = ProfileAdministration(scope, Stub(), { true }, { "self" }, {}, { endedFor = it }, { renamed = true }, {})
        api.saveUser(ManagedMusicUser("other", "Other", "member"), "Other", "new-password", FolderAccess("all"))
        assertNull(endedFor)
        api.saveUser(ManagedMusicUser("self", "Self", "admin"), "New name", "new-password", FolderAccess("all"))
        assertEquals("New name", endedFor)
        assertFalse(renamed)
    }
    @Test fun forbiddenResponseRevokesVisibleManagementPermissions() = runBlocking {
        var revoked = false
        val stub = Stub().apply { denied = true }
        val api = ProfileAdministration(scope, stub, { true }, { "self" }, { revoked = true }, {}, {}, {})
        assertTrue(runCatching { api.users() }.isFailure)
        assertTrue(revoked)
    }
    @Test fun allNewLibraryOperationsRequireAdministrator() = runBlocking {
        val api = ProfileAdministration(scope, Stub(), { false }, { "member" }, {}, {}, {}, {})
        val operations: List<suspend () -> Any?> = listOf(
            { api.authorizedDirectories() }, { api.childDirectories("/vol1/1000/Music") },
            { api.folderDetail("folder") }, { api.scanAllFolders() },
            { api.cancelTask("task") }, { api.retryTask("task") }, { api.rebuildSearchIndex() },
        )
        operations.forEach { assertTrue(runCatching { it() }.exceptionOrNull() is IllegalStateException) }
    }
    private class Stub : MusicAdministration {
        var denied = false
        override suspend fun authorizedDirectories() = emptyList<AuthorizedMusicDirectory>()
        override suspend fun childDirectories(parent: String) = emptyList<MusicDirectory>()
        override suspend fun folderDetail(guid: String) = MusicFolder(guid)
        override suspend fun scanAllFolders() {}
        override suspend fun cancelTask(taskId: String) {}
        override suspend fun retryTask(taskId: String) {}
        override suspend fun rebuildSearchIndex() = SearchIndexResult(0, 0, 0)
        override suspend fun scanTasks() = emptyList<MusicScanTask>()
        override suspend fun folders() = emptyList<MusicFolder>()
        override suspend fun saveFolder(original: MusicFolder?, path: String, metadataPreference: String, autoDownloadLyric: Boolean) = Unit
        override suspend fun removeFolder(guid: String) = Unit
        override suspend fun scanFolder(guid: String) = Unit
        override suspend fun users(): List<ManagedMusicUser> { if (denied) throw MusicError.Business(403, "Denied"); return emptyList() }
        override suspend fun saveUser(original: ManagedMusicUser?, username: String, password: String?, access: FolderAccess) = Unit
        override suspend fun defaultAccess() = FolderAccess()
        override suspend fun saveDefaultAccess(access: FolderAccess) = Unit
        override suspend fun serverSettings() = MusicServerSettings("Test NAS")
        override suspend fun saveServerSettings(value: MusicServerSettings) = Unit
    }
}
