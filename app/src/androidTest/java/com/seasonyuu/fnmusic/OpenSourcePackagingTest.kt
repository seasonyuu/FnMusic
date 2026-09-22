package com.seasonyuu.fnmusic

import androidx.test.platform.app.InstrumentationRegistry
import com.mikepenz.aboutlibraries.Libs
import org.junit.Assert.*
import org.junit.Test

class OpenSourcePackagingTest {
    @Test fun packagedCatalogIncludesNativeNoticesAndOfflineLicenseBodies() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val data = context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
        val libraries = Libs.Builder().withJson(data).build().libraries
        assertTrue(libraries.any { it.uniqueId == "com.squareup.okhttp3:okhttp" })
        assertTrue(libraries.any { it.uniqueId.startsWith("com.squareup.okio:") })
        listOf("accompanist-lyrics-core", "accompanist-lyrics-ui", "liquid-glass-widgets", "airplay2-sender", "mbedtls", "ed25519", "pyatv", "pair_ap").forEach { id ->
            assertTrue("Missing manual notice: $id", libraries.any { it.uniqueId == "fnmusic-$id" })
        }
        libraries.forEach { library ->
            assertTrue("Missing license: ${library.uniqueId}", library.licenses.isNotEmpty())
            library.licenses.forEach { assertFalse("Missing text: ${library.uniqueId}", it.licenseContent.isNullOrBlank()) }
        }
        val sender = libraries.single { it.uniqueId == "fnmusic-airplay2-sender" }
        assertTrue(sender.licenses.any { it.licenseContent.orEmpty().contains("THIRD-PARTY NOTICES") })
        assertFalse(libraries.any { it.uniqueId.contains("junit") || it.uniqueId.contains("mockwebserver") })
    }
}
