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

    @Test fun liquidGlassRestoresAndReturnsToSettings() {
        lateinit var navigation: MusicNavigationState
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            navigation = rememberSaveable(saver = MusicNavigationState.Saver) { MusicNavigationState() }
        }
        compose.runOnIdle {
            navigation.select(MusicDestination.Profile)
            navigation.push(page = MusicPage.LiquidGlass)
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals(MusicPage.LiquidGlass, navigation.current.page)
            navigation.pop()
            assertEquals(MusicPage.Appearance, navigation.current.page)
            navigation.pop()
            assertEquals(MusicPage.Root, navigation.current.page)
            assertEquals(MusicDestination.Profile, navigation.destination)
        }
    }

    @Test fun appearanceChildrenRestoreWithoutDuplicatingParent() {
        val restoration = StateRestorationTester(compose)
        lateinit var navigation: MusicNavigationState
        restoration.setContent {
            navigation = rememberSaveable(saver = MusicNavigationState.Saver) { MusicNavigationState() }
        }
        listOf(MusicPage.DisplayMode, MusicPage.ThemeColor, MusicPage.LiquidGlass).forEach { page ->
            compose.runOnIdle {
                navigation.select(MusicDestination.Profile)
                navigation.resetCurrent()
                navigation.push(page = MusicPage.Appearance)
                navigation.push(page = page)
                navigation.select(MusicDestination.Search)
            }
            restoration.emulateSavedInstanceStateRestore()
            compose.runOnIdle {
                navigation.select(MusicDestination.Profile)
                if (page == MusicPage.ThemeColor || page == MusicPage.DisplayMode) {
                    assertEquals(MusicPage.Appearance, navigation.current.page)
                    assertEquals(1, navigation.current.depth)
                    return@runOnIdle
                }
                assertEquals(page, navigation.current.page)
                assertEquals(2, navigation.current.depth)
                navigation.pop()
                assertEquals(MusicPage.Appearance, navigation.current.page)
                navigation.pop()
                assertFalse(navigation.canPop)
            }
        }
    }

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
            navigation.select(MusicDestination.Library)
            navigation.push(page = MusicPage.Playlists)
            navigation.push(detail = editor)
            editorId = navigation.current.id
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals(MusicDestination.Library, navigation.destination)
            assertEquals(editorId, navigation.current.id)
            assertEquals(editor, navigation.current.detail)
            navigation.pop()
            assertEquals(MusicPage.Playlists, navigation.current.page)
            navigation.pop()
            assertFalse(navigation.canPop)
            navigation.select(MusicDestination.Home)
            assertEquals(homeId, navigation.current.id)
            assertEquals(album, navigation.current.detail)
        }
    }

    @Test fun legacyAndMalformedSnapshotsFallBackToHome() {
        listOf(
            android.os.Bundle().apply { putString("destination", "Favorites") },
            android.os.Bundle().apply { putInt("version", 2); putString("destination", "More") },
            android.os.Bundle().apply { putInt("version", 2); putString("destination", "Home") },
        ).forEach { saved ->
            val navigation = MusicNavigationState.restoreNavigation(saved)
            assertEquals(MusicDestination.Home, navigation.destination)
            assertEquals(MusicPage.Root, navigation.current.page)
            assertFalse(navigation.canPop)
        }
    }

    @Test fun collectionsStayInTheirOriginStack() {
        val navigation = MusicNavigationState()
        navigation.select(MusicDestination.Library)
        navigation.push(page = MusicPage.Playlists)
        val libraryEntry = navigation.current.id
        navigation.select(MusicDestination.Home)
        navigation.push(page = MusicPage.Playlists)
        navigation.push(detail = LibraryDetail.PlaylistPage(Playlist(PlaylistId("p"), "Playlist")))
        navigation.pop()
        assertEquals(MusicPage.Playlists, navigation.current.page)
        navigation.pop()
        assertEquals(MusicDestination.Home, navigation.destination)
        assertFalse(navigation.canPop)
        navigation.select(MusicDestination.Library)
        assertEquals(libraryEntry, navigation.current.id)
    }

    @Test fun lateResponseForAnotherResourceCannotSupplyTracksOrErrors() {
        val oldKey = DetailRequestKey("album", "old")
        val target = DetailRequestKey("playlist", "new")
        val stale = MusicUiState(
            detailKey = oldKey,
            detailTracks = listOf(Track(TrackId("stale"), "Wrong song")),
            detailError = "Old failure",
            detailAlbum = Album(AlbumId("old"), "Old album", trackCount = 6),
            detailArtist = Artist(ArtistId("old"), "Old artist", trackCount = 65),
        )
        val visible = stale.forDetail(target)
        assertTrue(visible.detailTracks.isEmpty())
        assertNull(visible.detailError)
        assertNull(visible.detailAlbum)
        assertNull(visible.detailArtist)
        assertTrue(visible.detailLoading)
        val matching = stale.copy(detailKey = target, detailError = null)
        assertSame(matching, matching.forDetail(target))
    }
}
