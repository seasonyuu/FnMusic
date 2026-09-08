package com.seasonyuu.fnmusic.feature.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TrackMetadataEditorTest {
    @get:Rule val compose = createComposeRule()
    private val track = Track(TrackId("track"), "原名称", year = 2024, trackNo = 8, discNo = 1)

    @Test fun savesEditsAndRetainsDraftOnFailure() {
        var calls = 0
        var saved: TrackMetadata? = null
        compose.setContent { FnMusicTheme {
            TrackMetadataEditor(track, { TrackTagOptions(emptyList(), emptyList()) }, { original, edit ->
                calls++
                if (calls == 1) error("保存失败测试")
                assertEquals("新名称", edit.title)
                assertNull(edit.year)
                TrackMetadata(original.copy(title = edit.title, year = edit.year))
            }, { saved = it }, {})
        } }
        compose.onNodeWithText("名称").performTextReplacement("新名称")
        compose.onNodeWithText("年份").performScrollTo().performTextClearance()
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("保存失败测试").performScrollTo().assertIsDisplayed()
        assertNull(saved)
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals("新名称", saved?.track?.title); assertEquals(2, calls) }
    }

    @Test fun invalidNumbersBlockSaveAndCancelDoesNotWrite() {
        var writes = 0
        var dismissed = false
        compose.setContent { FnMusicTheme {
            TrackMetadataEditor(track, { TrackTagOptions(emptyList(), emptyList()) }, { original, _ ->
                writes++; TrackMetadata(original)
            }, {}, { dismissed = true })
        } }
        compose.onNodeWithText("年份").performScrollTo().performTextReplacement("-1")
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertTrue(dismissed); assertEquals(0, writes) }
    }
}
