package com.seasonyuu.fnmusic.feature.music

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.seasonyuu.fnmusic.core.designsystem.*
import com.seasonyuu.fnmusic.core.model.TrackSort
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TrackSortLiquidMenuTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun allFourSortsDeliverOriginalValuesAndRestoreSelectedState() {
        var selected by mutableStateOf(TrackSort.RecentlyAdded)
        val values = mutableListOf<TrackSort>()
        compose.setContent {
            FnMusicTheme {
                LiquidMenuHost {
                    CollectionPage(
                        "歌曲",
                        null,
                        { false },
                        actions = {
                            TrackSortMenu(selected) {
                                selected = it
                                values += it
                            }
                        },
                    ) { _, top ->
                        Column(Modifier.padding(top = top, start = 20.dp)) {
                            listOf("歌曲", "最新添加的音乐", "长日尽处", "城市的夜晚", "静谧时分").forEach {
                                Text(it, Modifier.padding(vertical = 16.dp))
                            }
                        }
                    }
                }
            }
        }
        val choices =
            listOf(
                TrackSort.RecentlyAdded,
                TrackSort.OldestAdded,
                TrackSort.TitleAscending,
                TrackSort.TitleDescending,
            )
        choices.forEach { sort ->
            compose.onNodeWithContentDescription("排序").performClick()
            compose.onNodeWithTag("liquid-menu-item-${selected.name}").assertIsSelected()
            if (sort == TrackSort.RecentlyAdded) {
                val ctx = InstrumentationRegistry.getInstrumentation().targetContext
                val dir = File(ctx.getExternalFilesDir(null), "liquid-menu").apply { mkdirs() }
                File(dir, "song-sort.png").outputStream().use {
                    compose
                        .onRoot()
                        .captureToImage()
                        .asAndroidBitmap()
                        .compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            compose.onNodeWithTag("liquid-menu-item-${sort.name}").performClick()
            compose.waitForIdle()
            assertEquals(sort, selected)
            compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
        }
        assertEquals(choices, values)
    }
}
