package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.designsystem.*
import com.seasonyuu.fnmusic.core.model.AlbumSort
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppBarLiquidMenuTest {
    @get:Rule val compose = createComposeRule()

    private fun fixture(actions: @Composable RowScope.() -> Unit) {
        compose.setContent {
            FnMusicTheme {
                LiquidMenuHost {
                    CollectionPage("本地测试", null, { false }, actions = actions) { _, top ->
                        Column(Modifier.padding(top = top, start = 20.dp)) {
                            listOf("专辑一", "专辑二", "本地歌单").forEach { Text(it, Modifier.padding(16.dp)) }
                        }
                    }
                }
            }
        }
    }

    @Test fun albumSortPreservesAllValuesAndSelection() {
        var selected by mutableStateOf(AlbumSort.RecentlyUpdated)
        val calls = mutableListOf<AlbumSort>()
        fixture { AlbumSortMenu(selected) { selected = it; calls += it } }
        val choices = listOf(AlbumSort.RecentlyUpdated, AlbumSort.OldestUpdated, AlbumSort.NameAscending, AlbumSort.NameDescending)
        for (sort in choices) {
            compose.onNodeWithContentDescription("专辑排序").performClick()
            compose.onNodeWithTag("liquid-menu-item-${selected.name}").assertIsSelected()
            compose.onNodeWithTag("liquid-menu-item-${sort.name}").performClick()
            compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
            assertEquals(sort, selected)
        }
        assertEquals(choices, calls)
        compose.onNodeWithContentDescription("专辑排序").performClick()
        compose.onNodeWithTag("liquid-menu-item-${selected.name}").assertIsSelected()
    }

    @Test fun refreshCallsBothExistingOperationsOncePerSelection() {
        var pagingRefresh = 0
        var externalRefresh = 0
        fixture { TrackRefreshMenu { pagingRefresh++; externalRefresh++ } }
        repeat(2) {
            compose.onNodeWithContentDescription("更多").performClick()
            compose.onNodeWithTag("liquid-menu-item-refresh").performClick()
            compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
            assertEquals(it + 1, pagingRefresh)
            assertEquals(it + 1, externalRefresh)
        }
    }

    @Test fun playlistActionsPreserveDisabledStateAndDeliverEachActionOnce() {
        var busy by mutableStateOf(true)
        val calls = mutableListOf<String>()
        fixture { PlaylistActionsMenu(busy, { calls += "edit" }, { calls += "purge" }, { calls += "delete" }) }
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithTag("liquid-menu-item-edit").assertIsEnabled()
        for (id in listOf("purge", "delete")) {
            compose.onNodeWithTag("liquid-menu-item-$id").assertIsNotEnabled().performTouchInput { click() }
            assertTrue(calls.isEmpty())
        }
        // Enabled state must refresh in an already-open menu.
        compose.runOnIdle { busy = false }
        compose.onNodeWithTag("liquid-menu-item-delete").assertIsEnabled()
        compose.onNodeWithTag("liquid-menu-item-edit").performClick()
        for (id in listOf("purge", "delete")) {
            compose.onNodeWithContentDescription("更多").performClick()
            compose.onNodeWithTag("liquid-menu-item-$id").performClick()
            compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
        }
        assertEquals(listOf("edit", "purge", "delete"), calls)
    }
}
