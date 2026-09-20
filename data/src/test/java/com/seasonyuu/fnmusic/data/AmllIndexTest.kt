package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.*
import org.junit.Assert.*
import org.junit.Test

internal fun indexRow(file: String = "100-author-one.ttml", id: String = "123", title: String = "Song") =
    """{"rawLyricFile":"$file","metadata":[["musicName",["$title"]],["artists",["Artist"]],["album",["Album"]],["ncmMusicId",["$id"]],["ttmlAuthorGithubLogin",["Author"]]]}"""
internal val lyricFixture = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div><p begin="1.000" end="4.000"><span begin="1.000" end="2.000">Hello</span> <span begin="2.500" end="3.500">world!</span><span ttm:role="x-translation">你好 世界！</span></p></div></body></tt>"""
internal fun song(title: String = "Song", album: String = "Album") = Track(TrackId("nas-song"), title,
    listOf(Artist(ArtistId("artist"), "Artist")), Album(AlbumId("album"), album))

class AmllIndexTest {
    @Test fun parsesMultivaluesAndResolvesRevisionsByPlatformId() {
        val rows = AmllIndex.parse(indexRow() + "\n" + indexRow("200-author-two.ttml"))
        assertEquals(listOf("200-author-two.ttml"), rows.map { it.rawFile })
        assertEquals(listOf("Author"), rows.single().authors)
    }
    @Test fun acceptsCollaborativeSubmissionFilenamesInIndexAndDownloadPaths() {
        val files = listOf(
            "1766419644119-207428447,45750526-ehjthdko.ttml",
            "1766919706394-207428447,250306205-c5kz09um.ttml",
            "1766923426251-207428447,250306205-cs30tblo.ttml",
        )
        val rows = AmllIndex.parse(files.mapIndexed { i, file -> indexRow(file, id = i.toString()) }.joinToString("\n"))
        assertEquals(files, rows.map { it.rawFile })
        rows.forEach { row ->
            listOf(LyricsFetchSource.Bikonoo, LyricsFetchSource.GitHub, LyricsFetchSource.Gdba).forEach { source ->
                assertEquals("raw-lyrics/${row.rawFile}", AmllIndex.path(source, row))
            }
        }
        listOf("../bad.ttml", "a/b.ttml", "a%2fb.ttml", "file.ttml?x=1", "file.ttml#x").forEach { file ->
            assertThrows(IllegalArgumentException::class.java) { AmllIndex.parse(indexRow(file)) }
        }
    }

    @Test fun exactMatchingNormalizesButNeverStripsVersionNames() {
        val rows = AmllIndex.parse(indexRow())
        assertNotNull(AmllIndex.match(rows, song(" ＳＯＮＧ ")))
        assertNull(AmllIndex.match(rows, song("Song (Live)")))
        assertNull(AmllIndex.match(listOf(rows.single().copy(titles = listOf("Song", "Song (Live)"))), song()))
        assertNull(AmllIndex.match(rows, song().copy(artists = emptyList())))
        val duplicates = rows + rows.single().copy(rawFile = "200-other.ttml", albums = listOf("Other"))
        assertEquals(rows.single(), AmllIndex.match(duplicates, song()))
        assertNull(AmllIndex.match(duplicates, song(album = "Missing")))
    }
    @Test fun matchesArtistAliasesFromRealIndexMetadata() {
        val rows = AmllIndex.parse("""{"metadata":[["album",["怪咖"]],["appleMusicId",["1787448195"]],["artists",["薛之谦","薛之謙","설지겸","Joker Xue"]],["isrc",["HKE022210689"]],["musicName",["怪咖"]],["ncmMusicId",["574921549"]],["qqMusicId",["003FFWnA3AIczD","214182478"]],["spotifyId",["7pDmqnifITVMuT46B6Vj8c"]],["ttmlAuthorGithub",["50987405"]],["ttmlAuthorGithubLogin",["ITManCHINA"]]],"rawLyricFile":"1762010143829-50987405-b8f04e68.ttml"}""")
        listOf("薛之谦", "薛之謙", "설지겸", "  JOKER   XUE ").forEach { name ->
            val track = song("怪咖", "怪咖").copy(artists = listOf(Artist(ArtistId("artist"), name)))
            assertEquals(rows.single(), AmllIndex.match(rows, track))
            assertNull(AmllIndex.match(rows, track.copy(title = "怪咖 (Live)")))
        }
    }

    @Test fun requiresEveryNasArtistDespiteAdditionalIndexAliases() {
        val row = AmllIndex.parse(indexRow()).single().copy(artists = listOf("Artist", "Alias", "Guest", "Guest Alias"))
        val duet = song().copy(artists = listOf(Artist(ArtistId("a"), "Artist"), Artist(ArtistId("b"), "Guest")))
        assertEquals(row, AmllIndex.match(listOf(row), duet))
        assertNull(AmllIndex.match(listOf(row.copy(artists = listOf("Artist", "Alias"))), duet))
        assertNull(AmllIndex.match(listOf(row.copy(artists = emptyList())), duet))
        assertNull(AmllIndex.match(listOf(row), song().copy(artists = listOf(Artist(ArtistId("a"), "Unknown")))))
    }

    @Test fun aliasMatchesStillRequireUniqueCandidateOrExactAlbum() {
        val row = AmllIndex.parse(indexRow()).single().copy(artists = listOf("Artist", "Alias"))
        val other = row.copy(rawFile = "200-other.ttml", albums = listOf("Other"))
        assertEquals(row, AmllIndex.match(listOf(row, other), song()))
        assertNull(AmllIndex.match(listOf(row, other), song(album = "Missing")))
        assertNull(AmllIndex.match(listOf(row, other.copy(albums = row.albums)), song()))
    }

    @Test fun mapsOnlySupportedPathsWithoutTraversal() {
        val candidate = AmllIndex.parse(indexRow()).single()
        assertEquals("ncm-lyrics/123.ttml", AmllIndex.path(LyricsFetchSource.Dimeta, candidate))
        listOf(LyricsFetchSource.Bikonoo, LyricsFetchSource.GitHub, LyricsFetchSource.Gdba).forEach {
            assertEquals("raw-lyrics/100-author-one.ttml", AmllIndex.path(it, candidate))
        }
        assertNull(AmllIndex.path(LyricsFetchSource.Dimeta, candidate.copy(platformIds = emptyMap())))
        assertThrows(IllegalArgumentException::class.java) { AmllIndex.parse(indexRow("../invalid.ttml")) }
        assertThrows(Exception::class.java) { AmllIndex.parse("<html>unavailable</html>") }
    }
    @Test fun fuzzySearchDoesNotChangeAutomaticMatching() {
        val rows = AmllIndex.parse(indexRow())
        assertEquals(1, AmllIndex.search(rows, "son art").size)
        assertNull(AmllIndex.match(rows, song("Son")))
    }
}
