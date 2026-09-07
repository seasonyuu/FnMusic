package com.seasonyuu.fnmusic.feature.music

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.seasonyuu.fnmusic.core.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MusicNavigationStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun configurationRestorePreservesAllStacksAndEntryIdentities() {
        val restoration = StateRestorationTester(compose)
        lateinit var navigation: MusicNavigationState
        restoration.setContent {
            navigation = rememberSaveable(saver = MusicNavigationState.Saver) { MusicNavigationState() }
        }
        val album = LibraryDetail.AlbumPage(Album(AlbumId("a"), "Album"))
        val editor = LibraryDetail.PlaylistEditorPage(null, TrackId("pending"))
        var homeId = ""
        var editorId = ""
        compose.runOnIdle {
            navigation.push(detail = album)
            homeId = navigation.current.id
            navigation.select(MusicDestination.More)
            navigation.push(morePage = MorePage.Playlists)
            navigation.push(detail = editor)
            editorId = navigation.current.id
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals(MusicDestination.More, navigation.destination)
            assertEquals(editorId, navigation.current.id)
            assertEquals(editor, navigation.current.detail)
            navigation.pop()
            assertEquals(MorePage.Playlists, navigation.current.morePage)
            navigation.pop()
            assertFalse(navigation.canPop)
            navigation.select(MusicDestination.Home)
            assertEquals(homeId, navigation.current.id)
            assertEquals(album, navigation.current.detail)
        }
    }

    @Test fun lateResponseForAnotherResourceCannotSupplyTracksOrErrors() {
        val oldKey = DetailRequestKey("album", "old")
        val target = DetailRequestKey("playlist", "new")
        val stale = MusicUiState(
            detailKey = oldKey,
            detailTracks = listOf(Track(TrackId("stale"), "Wrong song")),
            detailError = "Old failure",
        )
        val visible = stale.forDetail(target)
        assertTrue(visible.detailTracks.isEmpty())
        assertNull(visible.detailError)
        assertTrue(visible.detailLoading)
        val matching = stale.copy(detailKey = target, detailError = null)
        assertSame(matching, matching.forDetail(target))
    }
}
