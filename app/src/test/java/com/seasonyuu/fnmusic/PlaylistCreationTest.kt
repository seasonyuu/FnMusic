package com.seasonyuu.fnmusic

import com.seasonyuu.fnmusic.core.model.Playlist
import com.seasonyuu.fnmusic.core.model.PlaylistId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistCreationTest {
    private val created = Playlist(PlaylistId("created"), "旅途")

    @Test fun photoIsUploadedBeforeCreationAndDraftIsDiscarded() = runBlocking {
        val steps = mutableListOf<String>()
        val result = createPlaylistWithCover(
            "旅途", "playlist_default_1", "content://photo",
            prepare = { steps += "prepare:$it"; "/private/draft" },
            upload = { steps += "upload:$it"; "uploaded-cover" },
            uploadDefault = { error("unexpected default upload") },
            discard = { steps += "discard:$it" },
            onCoverUploaded = { steps += "checkpoint:$it" },
            create = { name, cover -> steps += "create:$name:$cover"; created },
        )
        assertEquals(created, result)
        assertEquals(listOf("prepare:content://photo", "upload:/private/draft", "checkpoint:uploaded-cover", "create:旅途:uploaded-cover", "discard:/private/draft"), steps)
    }

    @Test fun uploadFailurePreventsCreationAndDiscardsDraft() = runBlocking {
        val steps = mutableListOf<String>()
        val failure = runCatching {
            createPlaylistWithCover(
                "旅途", "playlist_default_1", "content://photo",
                prepare = { "/private/draft" },
                upload = { throw IllegalStateException("上传失败") },
                uploadDefault = { error("unexpected default upload") },
                discard = { steps += "discard:$it" },
                create = { _, _ -> steps += "create"; created },
            )
        }.exceptionOrNull()
        assertEquals("上传失败", failure?.message)
        assertEquals(listOf("discard:/private/draft"), steps)
    }

    @Test fun defaultCoverIsUploadedBeforeCreation() = runBlocking {
        val steps = mutableListOf<String>()
        createPlaylistWithCover(
            "旅途", "playlist_default_2", null,
            prepare = { error("unexpected prepare") },
            upload = { error("unexpected photo upload") },
            uploadDefault = { steps += "uploadDefault:$it"; "server-default" },
            discard = { steps += "discard" },
            create = { _, cover -> steps += "create:$cover"; created },
        )
        assertEquals(listOf("uploadDefault:playlist_default_2", "create:server-default"), steps)
    }

    @Test fun retryReusesUploadedCoverAfterCreationFailure() = runBlocking {
        var checkpoint: String? = null
        val steps = mutableListOf<String>()
        suspend fun attempt(cached: String?, create: suspend (String, String?) -> Playlist) = createPlaylistWithCover(
            "旅途", "playlist_default_1", null,
            prepare = { error("unexpected prepare") },
            upload = { error("unexpected photo upload") },
            uploadDefault = { steps += "upload"; "server-cover" },
            discard = { },
            uploadedCoverId = cached,
            onCoverUploaded = { checkpoint = it },
            create = create,
        )
        runCatching { attempt(null) { _, _ -> error("名称已存在") } }
        assertEquals("server-cover", checkpoint)
        attempt(checkpoint) { _, cover -> steps += "create:$cover"; created }
        assertEquals(listOf("upload", "create:server-cover"), steps)
    }
}
