package com.seasonyuu.fnmusic.feature.music

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import com.seasonyuu.fnmusic.core.network.NetworkRuntime
import com.seasonyuu.fnmusic.data.PlaylistCoverFiles
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@androidx.test.filters.SdkSuppress(minSdkVersion = 29)
class PlaylistCoverFilesTest {
    @Test fun defaultTemplatesProduceDistinctUploadablePngsMatchingPreviews() {
        val expectedHashes = listOf(
            "cfd192e1be333d8dcfaf4c0ddcf96169289d28aba86e119329d7fa75a0613a10",
            "c92aea6654d0c6ce0d4e376a69e2302882cf1ddb977f3e0dc7ca244eb8d777ca",
            "814dcae08b7092b385b0c9e570f19c41bbb953a6179f00a5e0bb7f08ca98cbd0",
            "13cd1dd75bdc633804427b4c3582ec9e97d6ddf1bd849a5e2e0f7e6b1b8cd133",
        )
        for (index in 1..4) {
            val id = "playlist_default_$index"
            val bytes = com.seasonyuu.fnmusic.data.DefaultPlaylistCover.bytes(context, id)
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(expectedHashes[index - 1], hash)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            try {
                assertTrue(bitmap.sameAs(com.seasonyuu.fnmusic.data.DefaultPlaylistCover.preview(context, id)))
                assertTrue(bytes.size in 1..(5 * 1024 * 1024))
                assertEquals(344, bitmap.width)
                assertEquals(344, bitmap.height)
            } finally { bitmap.recycle() }
        }
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = PlaylistCoverFiles(context, NetworkRuntime().api)

    private suspend fun withImage(bytes: ByteArray, test: suspend (String) -> Unit) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "fnmusic-cover-test-${System.nanoTime()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FnMusicTests")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        try {
            resolver.openOutputStream(uri)!!.use { it.write(bytes) }
            test(uri.toString())
        } finally { resolver.delete(uri, null, null) }
    }

    @Test fun photoIsCopiedToNoBackupAndDiscardNeverDeletesOriginal() = runBlocking {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val bytes = java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        withImage(bytes) { uri ->
            val file = File(files.prepare(uri))
            assertTrue(file.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath + "/"))
            assertArrayEquals(bytes, file.readBytes())
            files.discard(file.path)
            assertFalse(file.exists())
            context.contentResolver.openInputStream(android.net.Uri.parse(uri))!!.use { assertArrayEquals(bytes, it.readBytes()) }
        }
    }

    @Test fun oversizedAndInvalidImagesAreRejected() = runBlocking {
        withImage(ByteArray(5 * 1024 * 1024 + 1)) { uri ->
            assertTrue(runCatching { files.prepare(uri) }.exceptionOrNull()!!.message!!.contains("5 MiB"))
        }
        withImage(byteArrayOf(1, 2, 3)) { uri ->
            assertTrue(runCatching { files.prepare(uri) }.isFailure)
        }
    }

    @Test fun discardRefusesFilesOutsideItsOwnDraftDirectory() {
        val other = File.createTempFile("other-", ".png", context.cacheDir)
        try { files.discard(other.path); assertTrue(other.exists()) }
        finally { other.delete() }
    }
}
