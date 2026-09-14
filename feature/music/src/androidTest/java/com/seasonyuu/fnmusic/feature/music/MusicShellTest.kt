package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.paging.cachedIn
import androidx.paging.PagingData
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.model.PlayerState
import com.seasonyuu.fnmusic.core.model.PlayableTrack
import com.seasonyuu.fnmusic.core.model.PlaybackStatus
import com.seasonyuu.fnmusic.core.model.Album
import com.seasonyuu.fnmusic.core.model.AlbumId
import com.seasonyuu.fnmusic.core.model.Artist
import com.seasonyuu.fnmusic.core.model.ArtistId
import com.seasonyuu.fnmusic.core.model.AudioSpec
import com.seasonyuu.fnmusic.core.model.LyricLine
import com.seasonyuu.fnmusic.core.model.SearchSuggestions
import com.seasonyuu.fnmusic.core.model.SearchType
import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.TrackId
import com.seasonyuu.fnmusic.core.model.TrackMetadata
import com.seasonyuu.fnmusic.core.model.TrackSort
import com.seasonyuu.fnmusic.core.model.Playlist
import com.seasonyuu.fnmusic.core.model.PlaylistId
import com.seasonyuu.fnmusic.data.SearchItem
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class MusicShellTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun playerVolumeUsesSystemStepsAndStaysAtSameValueAfterRelease() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val original = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val maximum = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val minimum = if (android.os.Build.VERSION.SDK_INT >= 28) {
            audio.getStreamMinVolume(android.media.AudioManager.STREAM_MUSIC)
        } else 0
        try {
            val track = Track(TrackId("volume-steps"), "音量档位测试")
            setContent(playerState = PlayerState(
                queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                currentIndex = 0,
            ))
            compose.onNodeWithText(track.title).performClick()
            val slider = compose.onNodeWithTag("player-volume-slider")
            fun range() = slider.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo]
            assertEquals(minimum.toFloat()..maximum.toFloat(), range().range)
            assertEquals((maximum - minimum - 1).coerceAtLeast(0), range().steps)
            if (maximum > minimum) {
                slider.performTouchInput {
                    down(Offset(width * 0.2f, centerY))
                    moveTo(Offset(width * 0.63f, centerY), delayMillis = 100)
                }
                compose.waitForIdle()
                val dragged = range().current
                assertEquals(dragged.toInt().toFloat(), dragged, 0f)
                assertEquals(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat(), dragged, 0f)
                slider.performTouchInput { up() }
                compose.waitForIdle()
                assertEquals(dragged, range().current, 0f)
                slider.performTouchInput { click(Offset(width * 0.9f, centerY)) }
                compose.waitForIdle()
                assertEquals("A volume track tap must not jump to its absolute position", dragged, range().current, 0f)
                slider.performTouchInput {
                    down(center)
                    moveTo(Offset(width * 0.3f, centerY), delayMillis = 100)
                }
                val beforeCancel = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                slider.performTouchInput { cancel() }
                compose.waitForIdle()
                assertEquals(beforeCancel, audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
                assertEquals(beforeCancel.toFloat(), range().current, 0f)
            } else {
                slider.assertIsNotEnabled()
            }
        } finally {
            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, original, 0)
        }
    }

    @Test
    fun playerVolumeSlowDragAccumulatesSmallDeltasAcrossSteps() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val original = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val maximum = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val minimum = if (android.os.Build.VERSION.SDK_INT >= 28) {
            audio.getStreamMinVolume(android.media.AudioManager.STREAM_MUSIC)
        } else 0
        if (maximum - minimum < 2) return
        val startVolume = ((minimum + maximum) / 2).coerceAtMost(maximum - 1)
        try {
            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, startVolume, 0)
            val track = Track(TrackId("volume-slow-drag"), "音量慢速拖动")
            setContent(playerState = PlayerState(
                queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                currentIndex = 0,
            ))
            compose.onNodeWithText(track.title).performClick()
            val slider = compose.onNodeWithTag("player-volume-slider")
            val range = slider.fetchSemanticsNode().config[
                androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo
            ]
            assertEquals(startVolume.toFloat(), range.current, 0f)

            slider.performTouchInput {
                val startX = width * 0.1f
                val increment = width / (maximum - minimum).toFloat() * 0.2f
                down(Offset(startX, centerY))
                repeat(8) { index ->
                    moveTo(Offset(startX + (index + 1) * increment, centerY), delayMillis = 100)
                }
            }
            compose.waitForIdle()
            val dragged = slider.fetchSemanticsNode().config[
                androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo
            ].current
            assertTrue("Slow small deltas must move the stepped volume slider", dragged > startVolume)
            assertEquals(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat(), dragged, 0f)
            slider.performTouchInput { up() }
        } finally {
            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, original, 0)
        }
    }

    @Test
    fun timeEdgesAndVolumeInkGapsFollowTheTrackThroughoutExpansion() {
        val audio = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val originalVolume = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        try {
            val track = Track(TrackId("slider-alignment"), "滑条边缘对齐")
            setContent(playerState = PlayerState(
                queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                currentIndex = 0, positionMs = 90_000, durationMs = 180_000,
            ))
            compose.onNodeWithText(track.title).performClick()
            fun bounds(tag: String): androidx.compose.ui.geometry.Rect {
                val coordinates = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
                return coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
            }
            fun gaps(): Pair<Float, Float> {
                val trackBounds = bounds("player-volume-slider")
                val low = bounds("player-volume-low-icon")
                val high = bounds("player-volume-high-icon")
                return (trackBounds.left - (low.left + low.width * 18.5f / 24f)) to
                    (high.left + high.width * 3f / 24f - trackBounds.right)
            }
            val restingGaps = gaps()
            compose.mainClock.autoAdvance = false
            val progress = compose.onNodeWithTag("player-playback-progress")
            progress.performTouchInput { down(center) }
            repeat(8) {
                compose.mainClock.advanceTimeBy(32)
                val trackBounds = bounds("player-playback-progress")
                assertEquals(trackBounds.left, bounds("player-elapsed-time").left, 1f)
                assertEquals(trackBounds.right, bounds("player-remaining-time").right, 1f)
            }
            progress.performTouchInput { up() }
            repeat(8) {
                compose.mainClock.advanceTimeBy(32)
                val trackBounds = bounds("player-playback-progress")
                assertEquals(trackBounds.left, bounds("player-elapsed-time").left, 1f)
                assertEquals(trackBounds.right, bounds("player-remaining-time").right, 1f)
            }
            val volume = compose.onNodeWithTag("player-volume-slider")
            volume.performTouchInput { down(center) }
            repeat(8) {
                compose.mainClock.advanceTimeBy(32)
                val animated = gaps()
                assertEquals("Track=${bounds("player-volume-slider")}, low=${bounds("player-volume-low-icon")}, e=${volume.fetchSemanticsNode().config[PlayerSliderExpansion]}", restingGaps.first, animated.first, 1f)
                assertEquals(restingGaps.second, animated.second, 1f)
            }
            volume.performTouchInput { up() }
            repeat(8) {
                compose.mainClock.advanceTimeBy(32)
                val animated = gaps()
                assertEquals("Track=${bounds("player-volume-slider")}, low=${bounds("player-volume-low-icon")}, e=${volume.fetchSemanticsNode().config[PlayerSliderExpansion]}", restingGaps.first, animated.first, 1f)
                assertEquals(restingGaps.second, animated.second, 1f)
            }
        } finally {
            compose.mainClock.autoAdvance = true
            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, originalVolume, 0)
        }
    }

    @Test
    fun volumeEdgesDeformAndPulseOnceUntilReentered() {
        val audio = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val originalVolume = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        try {
            val track = Track(TrackId("slider-edge-pulse"), "音量边界反馈")
            setContent(playerState = PlayerState(
                queue = listOf(PlayableTrack(track, "https://music.invalid/stream")), currentIndex = 0,
            ))
            compose.onNodeWithText(track.title).performClick()
            val volume = compose.onNodeWithTag("player-volume-slider")
            volume.assertIsDisplayed()
            fun height(tag: String): Float {
                val coordinates = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
                return coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false).height
            }
            fun pull() = volume.fetchSemanticsNode().config[PlayerSliderPull]
            val lowTag = "player-volume-low-icon"
            val highTag = "player-volume-high-icon"
            compose.mainClock.autoAdvance = false
            volume.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(240)
            val lowHeight = height(lowTag)
            val highHeight = height(highTag)
            volume.performTouchInput { moveTo(Offset(width * 1.2f, centerY), delayMillis = 16) }
            compose.mainClock.advanceTimeBy(80)
            assertTrue("Maximum icon pulses on entering the edge", height(highTag) > highHeight * 1.1f)
            assertEquals(lowHeight, height(lowTag), 1f)
            assertTrue("Track follows the outward pull", pull() > 1f)
            assertEquals(audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC), audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
            compose.mainClock.advanceTimeBy(400)
            assertEquals(highHeight, height(highTag), 1f)
            volume.performTouchInput { moveTo(Offset(width * 1.4f, centerY), delayMillis = 16) }
            compose.mainClock.advanceTimeBy(80)
            assertEquals("Holding past the edge does not retrigger", highHeight, height(highTag), 1f)
            volume.performTouchInput { moveTo(center, delayMillis = 16) }
            compose.mainClock.advanceTimeBy(100)
            volume.performTouchInput { moveTo(Offset(-width * 0.2f, centerY), delayMillis = 16) }
            compose.mainClock.advanceTimeBy(80)
            assertTrue("Minimum icon pulses independently", height(lowTag) > lowHeight * 1.1f)
            assertEquals(highHeight, height(highTag), 1f)
            assertTrue(pull() < -1f)
            compose.mainClock.advanceTimeBy(400)
            volume.performTouchInput { moveTo(center, delayMillis = 16) }
            compose.mainClock.advanceTimeBy(100)
            volume.performTouchInput { moveTo(Offset(-width * 0.2f, centerY), delayMillis = 16) }
            compose.mainClock.advanceTimeBy(80)
            assertTrue("Returning from the middle rearms the pulse", height(lowTag) > lowHeight * 1.1f)
            volume.performTouchInput { cancel() }
            compose.mainClock.advanceTimeBy(800)
            assertEquals(0f, pull(), 0.5f)
            assertEquals(lowHeight / 1.15f, height(lowTag), 1f)
        } finally {
            compose.mainClock.autoAdvance = true
            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, originalVolume, 0)
        }
    }

    @Test
    fun sliderEndpointsScaleAndHighlightTogetherThenRestore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val originalVolume = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        try {
            val track = Track(TrackId("endpoint-feedback"), "滑条两端反馈")
            setContent(playerState = PlayerState(
                queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                currentIndex = 0, positionMs = 90_000, durationMs = 180_000,
            ))
            compose.onNodeWithText(track.title).performClick()
            val quality = compose.onNodeWithTag("player-quality").fetchSemanticsNode().boundsInRoot
            val transport = compose.onNodeWithTag("player-transport-controls").fetchSemanticsNode().boundsInRoot
            fun brightest(tag: String): Int {
                val bitmap = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
                var maximum = 0
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                    maximum = maxOf(maximum, android.graphics.Color.red(bitmap.getPixel(x, y)))
                }
                return maximum
            }
            for ((sliderTag, endpointTags) in listOf(
                "player-playback-progress" to listOf("player-elapsed-time", "player-remaining-time"),
                "player-volume-slider" to listOf("player-volume-low-icon", "player-volume-high-icon"),
            )) {
                val resting = endpointTags.map { compose.onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
                val brightness = endpointTags.map(::brightest)
                compose.mainClock.autoAdvance = false
                val slider = compose.onNodeWithTag(sliderTag)
                slider.performTouchInput { down(center) }
                compose.mainClock.advanceTimeBy(240)
                endpointTags.forEachIndexed { index, tag ->
                    val expanded = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertEquals("$tag grows in place", resting[index].height * 1.15f, expanded.height, 1f)
                    assertEquals(resting[index].center.y, expanded.center.y, 1f)
                    assertTrue("$tag becomes visibly brighter", brightest(tag) > brightness[index] + 30)
                }
                assertEquals(quality, compose.onNodeWithTag("player-quality").fetchSemanticsNode().boundsInRoot)
                assertEquals(transport, compose.onNodeWithTag("player-transport-controls").fetchSemanticsNode().boundsInRoot)
                slider.performTouchInput { up() }
                compose.mainClock.advanceTimeBy(300)
                endpointTags.forEachIndexed { index, tag ->
                    val restored = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertEquals(resting[index].height, restored.height, 1f)
                    assertTrue("$tag restores its muted color", kotlin.math.abs(brightest(tag) - brightness[index]) < 5)
                }
                compose.mainClock.autoAdvance = true
            }
        } finally {
            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, originalVolume, 0)
        }
    }

    @Test
    fun expandedPlaybackTrackIsNotClippedByTheControlsContainer() {
        val track = Track(TrackId("expanded-track"), "进度条圆角验证")
        setContent(playerState = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0, positionMs = 90_000, durationMs = 180_000,
        ))
        compose.onNodeWithText(track.title).performClick()
        val slider = compose.onNodeWithTag("player-playback-progress")
        val resting = slider.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        slider.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(240)
        val root = compose.onRoot()
        val rootBounds = root.fetchSemanticsNode().boundsInRoot
        val bitmap = root.captureToImage().asAndroidBitmap()
        val pixel = bitmap.getPixel(
            (resting.left - rootBounds.left - 4).toInt(),
            (resting.center.y - rootBounds.top).toInt(),
        )
        assertTrue("Expanded white ink must extend outside the resting controls bounds", android.graphics.Color.red(pixel) > 230)
        slider.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(300)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun wideMiniPlayerControlsStayOrderedAndDoNotOpenPlayer() {
        val calls = mutableListOf<String>()
        val tracks = (0..2).map { PlayableTrack(Track(TrackId("mini-$it"), "宽屏歌曲 $it"), "https://music.invalid/$it") }
        setContent(
            windowSize = { androidx.compose.ui.unit.DpSize(850.dp, 800.dp) },
            playerState = PlayerState(queue = tracks, currentIndex = 1),
            onToggleShuffle = { calls += "shuffle" },
            onPrevious = { calls += "previous" },
            onTogglePlayback = { calls += "toggle" },
            onNext = { calls += "next" },
            onCycleRepeatMode = { calls += "repeat" },
        )
        val order = listOf("shuffle", "previous", "toggle", "next", "repeat", "lyrics", "queue")
        val bounds = order.map { compose.onNodeWithTag("wide-mini-$it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        bounds.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
        val pill = compose.onNodeWithTag("wide-mini-player").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(pill.left > root.left && pill.right < root.right)
        order.take(5).forEach { compose.onNodeWithTag("wide-mini-$it").performTouchInput { click() } }
        compose.runOnIdle { assertEquals(order.take(5), calls) }
        compose.onNodeWithTag("player-playback-progress").assertDoesNotExist()
    }

    @Test
    fun wideMiniPlayerLyricsAndQueueOpenTheirOwnPages() {
        val input = androidx.navigationevent.DirectNavigationEventInput()
        val track = PlayableTrack(Track(TrackId("mini-pages"), "宽屏入口测试"), "https://music.invalid/stream")
        setContent(
            backInput = input,
            windowSize = { androidx.compose.ui.unit.DpSize(850.dp, 800.dp) },
            state = MusicUiState(loading = false, lyrics = listOf(LyricLine(text = "直接打开歌词"))),
            playerState = PlayerState(queue = listOf(track), currentIndex = 0),
        )
        compose.onNodeWithTag("wide-mini-lyrics").performTouchInput { click() }
        compose.onNodeWithTag("lyrics-list").assertIsDisplayed()
        compose.runOnIdle {
            input.backStarted(androidx.navigationevent.NavigationEvent(progress = 0f, swipeEdge = androidx.navigationevent.NavigationEvent.EDGE_LEFT))
        }
        compose.runOnIdle { input.backCompleted() }
        compose.onNodeWithTag("wide-mini-queue").performTouchInput { click() }
        compose.onNodeWithTag("player-queue-list").assertIsDisplayed()
    }

    @Test
    fun wideMiniPlayerRoamDisablesModesAndQueueWithoutOpeningPlayer() {
        var modeRequests = 0
        val track = PlayableTrack(Track(TrackId("mini-roam"), "漫游入口测试"), "https://music.invalid/stream")
        setContent(
            windowSize = { androidx.compose.ui.unit.DpSize(850.dp, 800.dp) },
            playerState = PlayerState(queue = listOf(track), currentIndex = 0, isRoaming = true),
            onToggleShuffle = { modeRequests++ },
            onCycleRepeatMode = { modeRequests++ },
        )
        listOf("shuffle", "repeat", "queue").forEach {
            compose.onNodeWithTag("wide-mini-$it").assertIsNotEnabled().performTouchInput { click() }
        }
        compose.onNodeWithContentDescription("漫游模式").assertIsDisplayed()
        compose.onNodeWithTag("wide-mini-next").assertIsEnabled()
        compose.onNodeWithTag("wide-mini-lyrics").assertIsEnabled()
        compose.onNodeWithTag("player-playback-progress").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, modeRequests) }
    }

    @Test
    fun portraitTwentyByNineKeepsArtworkControlsAndLyricsAligned() {
        val window = mutableStateOf(androidx.compose.ui.unit.DpSize(390.dp, (390f * 20f / 9f).dp))
        val track = Track(TrackId("portrait-alignment"), "竖屏对齐")
        setContent(
            windowSize = { window.value },
            playerState = PlayerState(
                queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                currentIndex = 0,
            ),
            state = MusicUiState(loading = false, lyrics = listOf(LyricLine(text = "统一的歌词边距"))),
        )
        compose.onNodeWithText(track.title).performClick()
        for (width in listOf(360f, 390f, 443f)) {
            compose.runOnIdle { window.value = androidx.compose.ui.unit.DpSize(width.dp, (width * 20f / 9f).dp) }
            val root = compose.onNodeWithTag("player-morph-overlay").fetchSemanticsNode().boundsInRoot
            val density = root.width / width
            val metadata = compose.onNodeWithTag("player-track-metadata").fetchSemanticsNode().boundsInRoot
            val controls = compose.onNodeWithTag("player-transport-controls").fetchSemanticsNode().boundsInRoot
            val cover = compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot
            assertEquals(root.left + 40f * density, metadata.left, 2f)
            assertEquals(root.right - 40f * density, metadata.right, 2f)
            assertEquals(metadata.left, controls.left, 1f)
            assertEquals(metadata.right, controls.right, 1f)
            assertEquals("Unscaled artwork shares the text gutter", metadata.width * 0.73f, cover.width, 2f)
            compose.onNodeWithTag("player-lyrics-entry").performClick()
            val lyrics = compose.onNodeWithText("统一的歌词边距", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(metadata.left, lyrics.left, 2f)
            val smallCover = compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot
            assertEquals(metadata.left, smallCover.left, 2f)
            compose.onNodeWithTag("player-morph-cover").performClick()
            compose.onNodeWithTag("player-track-metadata").assertIsDisplayed()
            compose.onNodeWithTag("player-queue-entry").performClick()
            val queueHeader = compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot
            assertEquals("Queue cover uses the same inset as queue rows", root.left + 24f * density, queueHeader.left, 2f)
            assertEquals("Queue header right matches queue rows", root.right - 24f * density, queueHeader.right, 2f)
            val more = compose.onNodeWithTag("player-lyrics-more-action", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals("Queue actions share the trailing button center", root.right - 44f * density, more.center.x, 2f)
            compose.onNodeWithTag("player-queue-header").performClick()
        }
    }

    @Test
    fun largeCoverTogglesPlaybackAcrossLayoutsWithoutChangingSmallCoverAction() {
        val window = mutableStateOf(androidx.compose.ui.unit.DpSize(390.dp, 844.dp))
        val track = Track(TrackId("cover-toggle"), "封面播放控制")
        var toggles = 0
        setContent(
            windowSize = { window.value },
            playerState = PlayerState(
                queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                currentIndex = 0,
            ),
            onTogglePlayback = { toggles++ },
            state = MusicUiState(loading = false, lyrics = listOf(LyricLine(text = "封面交互测试"))),
        )
        compose.onNodeWithText(track.title).performClick()
        compose.onNodeWithTag("player-morph-cover").performClick()
        compose.runOnIdle { assertEquals(1, toggles) }
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.onNodeWithTag("player-morph-cover").performClick()
        compose.onNodeWithTag("player-playback-progress").assertIsDisplayed()
        compose.runOnIdle { assertEquals("Small cover returns to playback without toggling", 1, toggles) }
        for (size in listOf(
            androidx.compose.ui.unit.DpSize(850.dp, 411.dp),
            androidx.compose.ui.unit.DpSize(1100.dp, 800.dp),
        )) {
            compose.runOnIdle { window.value = size }
            compose.onNodeWithTag("player-morph-cover").performClick()
            compose.onNodeWithTag("player-lyrics-entry").performClick()
            compose.onNodeWithTag("lyrics-list").assertIsDisplayed()
            compose.onNodeWithTag("player-morph-cover").performClick()
            compose.onNodeWithTag("lyrics-list").assertIsDisplayed()
        }
        compose.runOnIdle { assertEquals(5, toggles) }
    }

    @Test
    fun shortLandscapePlayerKeepsLargeCoverAndFixedPageSwitches() {
        val track = Track(TrackId("short-wide-artwork"), "横屏下仍然清晰可见的专辑封面")
        setContent(
            windowSize = { androidx.compose.ui.unit.DpSize(850.dp, 411.dp) },
            playerState =
                PlayerState(
                    queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                    currentIndex = 0
                ),
            state = MusicUiState(loading = false, lyrics = listOf(LyricLine(text = "横屏歌词"))),
        )
        compose.onNodeWithText(track.title).performClick()
        val cover = compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot
        captureLyricsScreenshot("short-landscape-playback")
        val renderedDensity = compose.onNodeWithTag("player-morph-overlay").fetchSemanticsNode().boundsInRoot.width / 850f
        assertTrue(
            "Paused landscape artwork should remain at least 200dp: $cover",
            cover.width >= 200f * renderedDensity,
        )
        val metadata =
            compose
                .onNodeWithTag("player-track-metadata")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue("Track information should sit beside the artwork", metadata.left >= cover.right)
        val artworkPane = compose.onNodeWithTag("player-primary-pane").fetchSemanticsNode().boundsInRoot
        assertEquals("Metadata aligns with the unscaled artwork", artworkPane.top, metadata.top, 1f)
        assertEquals("Unscaled artwork uses the full available height", artworkPane.height, artworkPane.width, 1f)
        assertEquals("Pause keeps the existing artwork scale", artworkPane.height * 0.73f, cover.height, 2f)
        val progress = compose.onNodeWithTag("player-transport-controls").fetchSemanticsNode().boundsInRoot
        assertEquals(metadata.left, progress.left, 1f)
        assertEquals(metadata.right, progress.right, 1f)
        compose.onNodeWithContentDescription("播放或暂停").assertIsDisplayed()
        compose.onNodeWithTag("player-volume-slider").assertIsDisplayed()
        compose.onNodeWithTag("player-playback-entry").assertDoesNotExist()
        val footer =
            compose.onNodeWithTag("player-bottom-utilities").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.onNodeWithTag("lyrics-list").assertIsDisplayed()
        val header = compose.onNodeWithTag("player-landscape-lyrics-header").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val lyricsPane = compose.onNodeWithTag("player-detail-pane").fetchSemanticsNode().boundsInRoot
        assertTrue("Lyrics stay below the song information", header.bottom <= lyricsPane.top)
        val lyricText = compose.onNodeWithText("横屏歌词", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(metadata.left, lyricText.left, 1f)
        compose.onNodeWithTag("player-playback-progress").assertDoesNotExist()
        compose.onNodeWithTag("player-track-metadata").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(5_000)
        compose.onNodeWithTag("player-bottom-utilities").assertDoesNotExist()
        compose.onNodeWithTag("player-landscape-lyrics-header").assertIsDisplayed()
        val expandedLyricsPane = compose.onNodeWithTag("player-detail-pane").fetchSemanticsNode().boundsInRoot
        assertEquals(lyricsPane.top, expandedLyricsPane.top, 1f)
        assertEquals(88f * renderedDensity, expandedLyricsPane.bottom - lyricsPane.bottom, 2f)
        assertEquals(cover, compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("player-detail-pane").performTouchInput { click() }
        compose.onNodeWithTag("player-lyrics-entry").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithTag("player-queue-entry").assertIsDisplayed().performClick()
        compose.onNodeWithTag("player-queue-list").assertIsDisplayed()
        compose.onNodeWithTag("player-queue-header").assertDoesNotExist()
        compose.onNodeWithTag("player-playback-progress").assertDoesNotExist()
        assertEquals(
            cover,
            compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot
        )
        assertEquals(
            footer,
            compose.onNodeWithTag("player-bottom-utilities").fetchSemanticsNode().boundsInRoot
        )
        compose.onNodeWithTag("player-queue-entry").performClick()
        compose.onNodeWithContentDescription("播放或暂停").assertIsDisplayed()
        compose.onNodeWithTag("player-playback-entry").assertDoesNotExist()
    }

    @Test
    fun landscapePageExitNeverOverlaysPlaybackControls() {
        val track = Track(TrackId("landscape-page-exit"), "横屏页面退出")
        setContent(
            windowSize = { androidx.compose.ui.unit.DpSize(850.dp, 411.dp) },
            playerState = PlayerState(
                queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                currentIndex = 0,
            ),
            state = MusicUiState(loading = false, lyrics = listOf(LyricLine(text = "退出时不能叠在控制上"))),
        )
        compose.onNodeWithText(track.title).performClick()
        compose.onNodeWithTag("player-lyrics-entry").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        fun pageAlpha() = compose.onNodeWithTag("player-detail-pane").fetchSemanticsNode().config[PlayerPageAlphaKey]
        for (idleFirst in listOf(false, true)) {
            compose.onNodeWithTag("player-lyrics-entry").performClick()
            compose.mainClock.advanceTimeBy(600)
            compose.onNodeWithTag("lyrics-list").assertIsDisplayed()
            if (idleFirst) {
                compose.mainClock.advanceTimeBy(5_000)
                compose.onNodeWithTag("player-detail-pane").performTouchInput { click() }
                compose.mainClock.advanceTimeBy(240)
            }
            val outgoingLine = compose.onNodeWithTag("lyrics-line-0").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("player-lyrics-entry").performClick()
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithTag("lyrics-list").assertIsDisplayed()
            assertTrue("Outgoing lyrics must actually fade", pageAlpha() in 0.01f..0.99f)
            compose.onNodeWithTag("player-landscape-controls").assertDoesNotExist()
            assertEquals(outgoingLine, compose.onNodeWithTag("lyrics-line-0").fetchSemanticsNode().boundsInRoot)
            compose.mainClock.advanceTimeBy(400)
            repeat(16) {
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithTag("player-landscape-controls").assertIsDisplayed()
                compose.onNodeWithTag("lyrics-list").assertDoesNotExist()
            }
        }
        compose.onNodeWithTag("player-queue-entry").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("player-queue-entry").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("player-queue-list").assertIsDisplayed()
        compose.onNodeWithTag("player-landscape-controls").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(400)
        repeat(16) {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("player-landscape-controls").assertIsDisplayed()
            compose.onNodeWithTag("player-queue-list").assertDoesNotExist()
        }
        // Reverse an exit before it finishes; the latest request must win without a flash.
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        repeat(24) {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("lyrics-list").assertIsDisplayed()
            compose.onNodeWithTag("player-landscape-controls").assertDoesNotExist()
        }
        assertEquals(1f, pageAlpha(), 0.001f)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun landscapeLyricsIdleTimerResetsOnTouchAndKeepsOtherPagesVisible() {
        val track = Track(TrackId("landscape-idle"), "歌词空闲计时")
        setContent(
            windowSize = { androidx.compose.ui.unit.DpSize(850.dp, 411.dp) },
            playerState = PlayerState(queue = listOf(PlayableTrack(track, "https://music.invalid/stream")), currentIndex = 0),
        )
        compose.onNodeWithText(track.title).performClick()
        compose.mainClock.advanceTimeBy(5_000)
        compose.onNodeWithTag("player-lyrics-entry").assertIsDisplayed()
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.mainClock.advanceTimeBy(5_000)
        compose.onNodeWithTag("player-bottom-utilities").assertDoesNotExist()
        compose.onNodeWithTag("player-detail-pane").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(5_000)
        compose.onNodeWithTag("player-lyrics-entry").assertIsDisplayed()
        compose.onNodeWithTag("player-detail-pane").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("player-lyrics-entry").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("player-bottom-utilities").assertDoesNotExist()
        compose.onNodeWithTag("player-detail-pane").performTouchInput { click() }
        compose.onNodeWithTag("player-queue-entry").performClick()
        compose.mainClock.advanceTimeBy(5_000)
        compose.onNodeWithTag("player-queue-entry").assertIsDisplayed()
    }

    @Test
    fun landscapePagesSurviveRotationAndRoamingKeepsItsGuard() {
        val size = mutableStateOf(androidx.compose.ui.unit.DpSize(800.dp, 360.dp))
        val track = Track(TrackId("landscape-rotate"), "横屏漫游")
        setContent(
            windowSize = { size.value },
            playerState =
                PlayerState(
                    queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                    currentIndex = 0,
                    isRoaming = true
                ),
        )
        compose.onNodeWithText(track.title).performClick()
        compose.onNodeWithTag("player-playback-entry").assertDoesNotExist()
        compose.onNodeWithTag("player-roam-indicator").assertIsDisplayed()
        compose.onNodeWithTag("player-queue-entry").assertDoesNotExist()
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.onNodeWithText("这首歌暂无歌词").assertIsDisplayed()
        compose.runOnIdle { size.value = androidx.compose.ui.unit.DpSize(390.dp, 844.dp) }
        compose.onNodeWithTag("player-lyrics-header").assertIsDisplayed()
        compose.onNodeWithText("这首歌暂无歌词").assertIsDisplayed()
        compose.runOnIdle { size.value = androidx.compose.ui.unit.DpSize(800.dp, 360.dp) }
        compose.onNodeWithTag("player-lyrics-entry").assertIsDisplayed()
        compose.onNodeWithTag("player-lyrics-entry").assertIsSelected().performClick()
        compose.onNodeWithContentDescription("播放或暂停").assertIsDisplayed()
        compose.onNodeWithTag("player-playback-entry").assertDoesNotExist()
    }
    @Test
    fun adaptivePlayerKeepsControlsAndCoverWhileSwitchingRightPane() {
        val track = Track(TrackId("adaptive"), "响应式播放测试")
        val player =
            PlayerState(
                queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                currentIndex = 0,
                durationMs = 180_000
            )
        setContent(
            windowSize = { androidx.compose.ui.unit.DpSize(1024.dp, 768.dp) },
            state = MusicUiState(loading = false, lyrics = listOf(LyricLine(text = "右侧歌词"))),
            playerState = player,
        )
        compose.onNodeWithText(track.title).performClick()
        compose.onNodeWithTag("lyrics-list").assertIsDisplayed()
        compose.onNodeWithTag("player-lyrics-entry").assertIsSelected()
        compose.onNodeWithTag("player-lyrics-header").assertDoesNotExist()
        val cover = compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("player-detail-pane").fetchSemanticsNode().boundsInRoot
        assertTrue(cover.right < right.left)
        compose.mainClock.advanceTimeBy(5_000)
        compose.onNodeWithTag("player-playback-progress").assertIsDisplayed()
        captureLyricsScreenshot("adaptive-wide-lyrics")
        compose.onNodeWithTag("player-queue-entry").performClick()
        compose.onNodeWithTag("player-queue-list").assertIsDisplayed()
        compose.onNodeWithTag("player-queue-header").assertDoesNotExist()
        assertEquals(
            cover,
            compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot
        )
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.onNodeWithTag("lyrics-list").assertIsDisplayed()
    }

    @Test
    fun adaptivePlayerPreservesLyricsAndQueueAcrossWindowChanges() {
        val size = mutableStateOf(androidx.compose.ui.unit.DpSize(1024.dp, 768.dp))
        val track = Track(TrackId("adaptive-resize"), "窗口调整测试")
        setContent(
            windowSize = { size.value },
            state = MusicUiState(loading = false, lyrics = listOf(LyricLine(text = "保留歌词"))),
            playerState =
                PlayerState(
                    queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                    currentIndex = 0
                ),
        )
        compose.onNodeWithText(track.title).performClick()
        compose.runOnIdle { size.value = androidx.compose.ui.unit.DpSize(390.dp, 844.dp) }
        compose.onNodeWithTag("player-lyrics-header").assertIsDisplayed()
        compose.onNodeWithTag("lyrics-list").assertIsDisplayed()
        compose.onNodeWithTag("player-queue-entry").performClick()
        compose.runOnIdle { size.value = androidx.compose.ui.unit.DpSize(1024.dp, 768.dp) }
        compose.onNodeWithTag("player-queue-list").assertIsDisplayed()
        compose.onNodeWithTag("player-queue-header").assertDoesNotExist()
        compose.onNodeWithTag("player-playback-progress").assertIsDisplayed()
    }

    @Test
    fun adaptiveShortWindowAndLargeFontKeepControlsReachable() {
        val track = Track(TrackId("adaptive-short"), "这是一首标题很长很长的歌曲用于验证大字体不会遮挡播放按钮")
        setContent(
            windowSize = { androidx.compose.ui.unit.DpSize(800.dp, 360.dp) },
            testFontScale = { 2f },
            playerState =
                PlayerState(
                    queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                    currentIndex = 0
                ),
        )
        compose.onNodeWithText(track.title).performClick()
        compose.onNodeWithContentDescription("播放或暂停").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("player-volume-slider").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("player-lyrics-entry").assertIsDisplayed()
        captureLyricsScreenshot("adaptive-short-large-font")
    }

    @Test
    fun adaptivePlayerCoversWindowAndFontMatrix() {
        val size = mutableStateOf(androidx.compose.ui.unit.DpSize(1024.dp, 768.dp))
        val font = mutableStateOf(1f)
        val track =
            Track(
                TrackId("adaptive-matrix"),
                "很长的歌曲标题 · A song with a long title for responsive layout"
            )
        setContent(
            windowSize = { size.value },
            testFontScale = { font.value },
            state =
                MusicUiState(
                    loading = false,
                    lyrics =
                        listOf(
                            LyricLine(
                                text = "Nothing holds me down",
                                timeMs = 0L,
                                translation = "没有什么能够让我停下"
                            ),
                            LyricLine(
                                text =
                                    "Asking where the time has gone, dreaming with the lights on",
                                timeMs = 30_000L
                            ),
                            LyricLine(text = "让长句歌词在不同窗口宽度下自然换行，播放控制始终保持可操作", timeMs = 60_000L),
                        )
                ),
            playerState =
                PlayerState(
                    queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                    currentIndex = 0,
                    playbackStatus = PlaybackStatus.Paused,
                    durationMs = 180_000
                ),
        )
        compose.onNodeWithText(track.title).performClick()
        for ((width, height) in
            listOf(390 to 844, 800 to 360, 840 to 600, 1024 to 768, 1280 to 800)) {
            for (scale in listOf(1f, 1.3f, 2f)) {
                compose.runOnIdle {
                    size.value = androidx.compose.ui.unit.DpSize(width.dp, height.dp)
                    font.value = scale
                }
                if (width < 840 &&
                        compose
                            .onAllNodesWithTag("player-lyrics-header")
                            .fetchSemanticsNodes()
                            .isNotEmpty()
                ) {
                    compose.onNodeWithTag("player-lyrics-entry").performScrollTo().performClick()
                }
                compose
                    .onNodeWithTag("player-playback-progress")
                    .performScrollTo()
                    .assertIsDisplayed()
                compose.onNodeWithContentDescription("播放或暂停").performScrollTo().assertIsDisplayed()
                if (isShortLandscapePlayer(width.dp, height.dp)) {
                    compose.onNodeWithTag("player-lyrics-entry").assertIsDisplayed()
                } else {
                    compose.onNodeWithTag("player-lyrics-entry").performScrollTo().assertIsDisplayed()
                }
                if (width >= 840) {
                    compose.onNodeWithTag("lyrics-list").assertIsDisplayed()
                    val left =
                        compose
                            .onNodeWithTag("player-primary-pane")
                            .fetchSemanticsNode()
                            .boundsInRoot
                    val right =
                        compose
                            .onNodeWithTag("player-detail-pane")
                            .fetchSemanticsNode()
                            .boundsInRoot
                    assertTrue(
                        "Panes must not overlap at $width x $height / $scale",
                        left.right < right.left
                    )
                }
                captureLyricsScreenshot("adaptive-matrix-$width-$height-$scale")
            }
        }
    }

    @Test
    fun adaptiveTimedLyricsKeepControlsVisibleDuringPlayback() {
        val track = Track(TrackId("adaptive-playing"), "实时歌词测试")
        val player =
            mutableStateOf(
                PlayerState(
                    queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                    currentIndex = 0,
                    durationMs = 180_000
                )
            )
        setContent(
            windowSize = { androidx.compose.ui.unit.DpSize(1024.dp, 768.dp) },
            playerStateProvider = { player.value },
            state =
                MusicUiState(
                    loading = false,
                    lyrics = (0..5).map { LyricLine(text = "实时歌词第 $it 行", timeMs = it * 5_000L) }
                ),
        )
        compose.onNodeWithText(track.title).performClick()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { player.value = player.value.copy(playbackStatus = PlaybackStatus.Playing) }
        compose.mainClock.advanceTimeBy(6_000)
        compose.onNodeWithTag("lyrics-list").assertIsDisplayed()
        compose.onNodeWithTag("player-playback-progress").assertIsDisplayed()
        compose.onNodeWithContentDescription("播放或暂停").assertIsDisplayed()
        compose.runOnIdle { player.value = player.value.copy(playbackStatus = PlaybackStatus.Paused) }
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun adaptivePlayerRestoresQueueAndBackReturnsThroughLyrics() {
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        val input = androidx.navigationevent.DirectNavigationEventInput()
        val track = Track(TrackId("adaptive-restoration"), "恢复播放页面")
        setContent(
            windowSize = { androidx.compose.ui.unit.DpSize(1024.dp, 768.dp) },
            restoration = restoration,
            backInput = input,
            playerState =
                PlayerState(
                    queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                    currentIndex = 0
                ),
        )
        compose.onNodeWithText(track.title).performClick()
        compose.onNodeWithText("这首歌暂无歌词").assertIsDisplayed()
        compose.onNodeWithTag("player-queue-entry").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("player-queue-list").assertIsDisplayed()
        fun back() {
            compose.runOnIdle {
                input.backStarted(
                    androidx.navigationevent.NavigationEvent(
                        progress = 0f,
                        swipeEdge = androidx.navigationevent.NavigationEvent.EDGE_LEFT
                    )
                )
            }
            compose.runOnIdle { input.backCompleted() }
        }
        back()
        compose.onNodeWithText("这首歌暂无歌词").assertIsDisplayed()
        back()
        compose.onNodeWithTag("player-morph-overlay").assertDoesNotExist()
    }

    @Test
    fun adaptiveShortQueueCanReachAndSelectItsLastRow() {
        val tracks = (1..24).map { Track(TrackId("short-queue-$it"), "短窗口队列 $it") }
        var selected: Int? = null
        setContent(windowSize = { androidx.compose.ui.unit.DpSize(800.dp, 360.dp) },
            playerState = PlayerState(queue = tracks.map { PlayableTrack(it, "https://music.invalid/stream") }, currentIndex = 0),
            onSkipToQueueItem = { selected = it },
        )
        compose.onNodeWithText(tracks.first().title).performClick()
        compose.onNodeWithTag("player-queue-entry").assertIsDisplayed().performClick()
        compose.onNodeWithTag("player-queue-list").performScrollToNode(hasText(tracks.last().title))
        compose.onNodeWithText(tracks.last().title).assertIsDisplayed().performClick()
        assertEquals(23, selected)
    }

    @Test
    fun homeFavoriteCardOpensPagedFavorites() {
        setContent()

        compose.onNodeWithContentDescription("收藏 快捷入口").performClick()

        compose.onNodeWithText("0 首已加载歌曲").assertIsDisplayed()
    }

    @Test
    fun homeRoamCardDispatchesTheRoamAction() {
        var roamRequests = 0
        setContent(onRoam = { roamRequests += 1 })

        compose.onNodeWithContentDescription("随机漫游").performClick()

        org.junit.Assert.assertEquals(1, roamRequests)
    }

    @Test
    fun homeRecentlyAddedFlowCardOpensTheLibraryWithRecentSort() {
        var selectedSort: TrackSort? = null
        setContent(onTrackSort = { selectedSort = it })

        compose.onNodeWithTag("首页快捷入口").performScrollToNode(hasContentDescription("最近添加 快捷入口"))
        compose.onNodeWithContentDescription("最近添加 快捷入口").performClick()

        compose.runOnIdle { org.junit.Assert.assertEquals(TrackSort.RecentlyAdded, selectedSort) }
        compose.onNodeWithText("音乐库").assertIsDisplayed()
    }

    @Test
    fun homeUsesThreeRowRecentGridAndShowsCollectionLinks() {
        val tracks = (1..4).map { index -> Track(TrackId("track-$index"), "最近曲目 $index") }
        val playlist = Playlist(PlaylistId("playlist-placeholder"), "首页歌单", trackCount = 4)
        setContent(MusicUiState(loading = false, tracks = tracks, playlists = listOf(playlist)))

        val first = compose.onNodeWithText("最近曲目 1").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("最近曲目 2").fetchSemanticsNode().boundsInRoot
        val third = compose.onNodeWithText("最近曲目 3").fetchSemanticsNode().boundsInRoot
        val fourth = compose.onNodeWithText("最近曲目 4").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(first.left, second.left, 1f)
        org.junit.Assert.assertEquals(first.left, third.left, 1f)
        org.junit.Assert.assertTrue(second.top > first.top)
        org.junit.Assert.assertTrue(third.top > second.top)
        org.junit.Assert.assertTrue(fourth.left > first.left)
        compose.onNodeWithContentDescription("查看最近添加").assertIsDisplayed()
        compose.onNodeWithContentDescription("查看专辑").assertIsDisplayed()

        // Scroll the vertical page: performScrollTo on a card only targets its nested horizontal row.
        compose.onNodeWithTag("music-page-host").performTouchInput {
            swipe(Offset(center.x, height * .75f), Offset(center.x, height * .25f), 500)
        }
        compose.onNodeWithText("首页歌单").assertIsDisplayed().performClick()

        compose.onNodeWithText("我的歌单").assertIsDisplayed()
    }

    @Test
    fun moreRecentEntryOpensRecentTracksAndProvidesBackAction() {
        val recent = Track(TrackId("track-placeholder"), "测试曲目")
        setContent(MusicUiState(loading = false, recent = listOf(recent)))

        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("最近播放").performClick()

        compose.onNodeWithText("1 首歌曲").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").assertIsDisplayed()
    }

    @Test
    fun albumOpensAsFullContentPageInsteadOfDialog() {
        val album = Album(AlbumId("album-placeholder"), "测试专辑", trackCount = 1)
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        setContent(
            state = MusicUiState(loading = false, detailTracks = listOf(track)),
            albums = PagingData.from(listOf(album)),
        )

        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("全部专辑").performClick()
        compose.onNodeWithText("测试专辑").performClick()

        compose.onNodeWithText("测试专辑").assertIsDisplayed()
        compose.onNodeWithTag("album-play").assertIsDisplayed()
        compose.onNodeWithText("首页").assertIsDisplayed()
    }

    @Test
    fun miniPlayerOpensImmersivePlayerPage() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)

        compose.onNodeWithText("测试曲目").performClick()

        compose.onNodeWithText("正在播放").assertDoesNotExist()
        compose.onNodeWithContentDescription("收起播放器").assertDoesNotExist()
        compose.onNodeWithTag("player-drag-handle").assertIsDisplayed()
        compose.onNodeWithTag("top-system-bar-blur").assertDoesNotExist()
        compose.onNodeWithText("首页").assertDoesNotExist()
    }

    @Test
    fun fullPlayerPlaybackControlRemainsClickableInsideTheDragRegion() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        var toggleRequests = 0
        setContent(
            playerState = player,
            onTogglePlayback = { toggleRequests += 1 },
        )
        compose.onNodeWithText("测试曲目").performClick()

        compose.onNodeWithContentDescription("播放或暂停").performClick()

        org.junit.Assert.assertEquals(1, toggleRequests)
        compose.onNodeWithTag("player-morph-overlay").assertIsDisplayed()
    }

    @Test
    fun fullPlayerGlassActionsRemainClickableBesideTheDragRegions() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        var favoriteRequests = 0
        setContent(
            playerState = player,
            onToggleFavorite = { favoriteRequests += 1 },
        )
        compose.onNodeWithText("测试曲目").performClick()

        compose.onNodeWithTag("player-favorite-action").performClick()
        org.junit.Assert.assertEquals(1, favoriteRequests)

        compose.onNodeWithTag("player-more-action").performClick()
        compose.onNodeWithText("下一首播放").assertIsDisplayed()
    }

    @Test
    fun shortPlayerDragReboundsWithoutClosing() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)
        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithTag("player-drag-handle").assertIsDisplayed()

        compose.onNodeWithTag("player-drag-handle").performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, 120f),
                durationMillis = 600,
            )
        }

        compose.onNodeWithTag("player-morph-overlay").assertIsDisplayed()
        compose.onNodeWithTag("player-drag-handle").assertIsDisplayed()
    }

    @Test
    fun longPlayerDragReturnsToMiniPlayer() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)
        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithTag("player-drag-handle").assertIsDisplayed()

        compose.onNodeWithTag("player-drag-handle").performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, 700f),
                durationMillis = 500,
            )
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("player-morph-overlay").fetchSemanticsNodes().isEmpty()
        }

        compose.onNodeWithText("测试曲目").assertIsDisplayed()
        compose.onNodeWithText("首页").assertIsDisplayed()
    }

    @Test
    fun draggingThePlayerBodyOutsideTheDedicatedRegionsDoesNotCloseIt() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)
        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithTag("player-morph-overlay").assertIsDisplayed()

        compose.onNodeWithTag("player-morph-overlay").performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, 700f),
                durationMillis = 500,
            )
        }
        compose.onNodeWithTag("player-morph-overlay").assertIsDisplayed()
        compose.onNodeWithText("首页").assertDoesNotExist()
    }

    @Test
    fun draggingTheRenderedLargeCoverReturnsToMiniPlayer() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)
        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithTag("player-morph-cover").assertIsDisplayed()
        compose.onNodeWithTag("player-drag-handle").assertIsDisplayed()

        compose.onNodeWithTag("player-morph-cover").performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, 700f),
                durationMillis = 650,
            )
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("player-morph-overlay").fetchSemanticsNodes().isEmpty()
        }

        compose.onNodeWithText("首页").assertIsDisplayed()
    }

    @Test
    fun slowlyDraggingTheRenderedLargeCoverReturnsToMiniPlayer() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)
        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithTag("player-morph-cover").assertIsDisplayed()

        compose.onNodeWithTag("player-morph-cover").performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, 700f),
                durationMillis = 2_500,
            )
        }
        compose.waitUntil(timeoutMillis = 4_000) {
            compose.onAllNodesWithTag("player-morph-overlay").fetchSemanticsNodes().isEmpty()
        }

        compose.onNodeWithText("首页").assertIsDisplayed()
    }

    @Test
    fun releasedPlayerDragDoesNotApplyTheCoverOffsetTwice() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)
        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithTag("player-drag-handle").assertIsDisplayed()
        val expandedCover = compose.onNodeWithTag("player-morph-cover")
            .fetchSemanticsNode().boundsInRoot

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("player-drag-handle").performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, 700f),
                durationMillis = 500,
            )
        }
        val releasedCover = compose.onNodeWithTag("player-morph-cover")
            .fetchSemanticsNode().boundsInRoot

        val coverTravel = releasedCover.top - expandedCover.top
        org.junit.Assert.assertTrue(
            "封面只应跟随单次 700px 拖拽，实际移动了 ${coverTravel}px",
            coverTravel in 550f..850f,
        )

        compose.mainClock.advanceTimeBy(180)
        val settlingCover = compose.onNodeWithTag("player-morph-cover")
            .fetchSemanticsNode().boundsInRoot
        val rootBottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        org.junit.Assert.assertTrue(
            "收起动画中封面不应再次被拖拽位移推出屏幕",
            settlingCover.bottom <= rootBottom,
        )
    }

    @Test
    fun lyricsHandleDragKeepsCoverAttachedUntilReleaseAndClosesTogether() {
        verifyLyricsHandleDrag(closes = true)
    }

    @Test
    fun shortLyricsHandleDragReboundsWithCoverAttached() {
        verifyLyricsHandleDrag(closes = false)
    }

    @Test
    fun lyricsHeaderDragKeepsCoverAttachedAndClosesTogether() {
        verifyLyricsHandleDrag(closes = true, dragTag = "player-lyrics-header")
    }

    @Test
    fun shortLyricsHeaderDragReboundsWithoutOpeningPlayback() {
        verifyLyricsHandleDrag(closes = false, dragTag = "player-lyrics-header")
        compose.onNodeWithTag("player-lyrics-header").assertIsDisplayed().performTouchInput {
            down(center)
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("player-lyrics-header").assertDoesNotExist()
        compose.onNodeWithTag("player-morph-overlay").assertIsDisplayed()
    }

    @Test
    fun queueHeaderDragKeepsCoverAttachedAndClosesTogether() {
        verifyLyricsHandleDrag(closes = true, dragTag = "player-queue-header", inQueue = true)
    }

    @Test
    fun shortQueueHeaderDragReboundsAndStillAllowsTap() {
        verifyLyricsHandleDrag(closes = false, dragTag = "player-queue-header", inQueue = true)
        compose.onNodeWithTag("player-queue-header").assertIsDisplayed().performTouchInput {
            down(center)
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("player-queue-header").assertDoesNotExist()
        compose.onNodeWithTag("player-morph-overlay").assertIsDisplayed()
    }

    private fun verifyLyricsHandleDrag(
        closes: Boolean,
        dragTag: String = "player-drag-handle",
        inQueue: Boolean = false,
    ) {
        val track = Track(TrackId("lyrics-dismiss"), "歌词收回验证")
        setContent(
            state = MusicUiState(loading = false, lyrics = listOf(LyricLine(text = "第一句"))),
            playerState = PlayerState(
                queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                currentIndex = 0, durationMs = 180_000,
            ),
        )
        compose.onNodeWithText("歌词收回验证").performClick()
        compose.onNodeWithTag(if (inQueue) "player-queue-entry" else "player-lyrics-entry").performClick()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_500)
        val cover = compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode()
        val header = compose.onNodeWithTag(if (inQueue) "player-queue-header" else "player-lyrics-header").fetchSemanticsNode()
        val surface = compose.onNodeWithTag("player-morph-surface").fetchSemanticsNode()
        val content = compose.onNodeWithTag("player-morph-content").fetchSemanticsNode()
        val start = compose.onNodeWithTag(dragTag).fetchSemanticsNode().boundsInWindow.center
        val originalCover = cover.boundsInRoot
        val originalHeader = header.boundsInRoot
        var decor: android.view.View? = null
        compose.runOnUiThread {
            decor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).single().window.decorView
        }
        val downTime = android.os.SystemClock.uptimeMillis()
        var eventTime = downTime
        var position = start
        fun dispatch(action: Int) {
            compose.runOnUiThread {
                val event = android.view.MotionEvent.obtain(downTime, eventTime, action, position.x, position.y, 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                decor!!.dispatchTouchEvent(event)
                event.recycle()
            }
        }
        dispatch(android.view.MotionEvent.ACTION_DOWN)
        repeat(if (closes) 30 else 6) { index ->
            eventTime += 16
            position = start + Offset(0f, (index + 1) * 20f)
            dispatch(android.view.MotionEvent.ACTION_MOVE)
            compose.mainClock.advanceTimeBy(16)
        }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnUiThread {
            val scale = header.boundsInRoot.width / originalHeader.width
            assertEquals("拖住时封面保持页头的相对位置",
                (originalCover.top - originalHeader.top) * scale,
                cover.boundsInRoot.top - header.boundsInRoot.top, 3f)
            assertEquals("松手前不向迷你封面缩小", originalCover.width * scale, cover.boundsInRoot.width, 3f)
        }
        eventTime += 500
        dispatch(android.view.MotionEvent.ACTION_UP)
        repeat(8) {
            compose.mainClock.advanceTimeBy(16)
            compose.runOnUiThread {
                assertEquals("收回时内容与背景共享同一顶部", surface.boundsInRoot.top, content.boundsInRoot.top, 2f)
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        if (closes) {
            compose.onNodeWithTag("player-morph-overlay").assertDoesNotExist()
        } else {
            compose.onNodeWithTag("player-morph-overlay").assertIsDisplayed()
            assertEquals(originalCover.top, cover.boundsInRoot.top, 2f)
            assertEquals(originalCover.width, cover.boundsInRoot.width, 2f)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun bottomBarCollapsesIntoAlignedCapsulesAndExpandsAgain() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        val tracks = (1..12).map { index -> Track(TrackId("home-track-$index"), "首页曲目 $index") }
        setContent(
            state = MusicUiState(loading = false, tracks = tracks),
            playerState = player,
        )

        compose.onNodeWithTag("dynamic-primary-tabs").assertIsDisplayed()
        compose.onNodeWithTag("dynamic-primary-tab").assertDoesNotExist()
        val initialIndicatorCenter = compose.onNodeWithTag("liquid-bottom-tabs-indicator")
            .fetchSemanticsNode().boundsInRoot.center.x
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onNodeWithTag("liquid-bottom-tabs-indicator")
                .fetchSemanticsNode().boundsInRoot.center.x > initialIndicatorCenter + 100f
        }
        compose.onNodeWithText("首页").performClick()

        compose.onNodeWithTag("music-page-host").performTouchInput {
            swipe(
                start = Offset(center.x, center.y * 1.3f),
                end = Offset(center.x, center.y * 0.5f),
                durationMillis = 500,
            )
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("dynamic-primary-tab").fetchSemanticsNodes().isNotEmpty()
        }

        val primaryCenter = compose.onNodeWithTag("dynamic-primary-tab").fetchSemanticsNode().boundsInRoot.center.y
        val playerCenter = compose.onNodeWithTag("dynamic-mini-player").fetchSemanticsNode().boundsInRoot.center.y
        val searchCenter = compose.onNodeWithTag("dynamic-search").fetchSemanticsNode().boundsInRoot.center.y
        org.junit.Assert.assertEquals(primaryCenter, playerCenter, 2f)
        org.junit.Assert.assertEquals(primaryCenter, searchCenter, 2f)

        compose.onNodeWithTag("dynamic-primary-tab").performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("dynamic-primary-tabs").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("dynamic-primary-tab").fetchSemanticsNodes().isEmpty()
        }

        compose.onNodeWithTag("music-page-host").performTouchInput {
            swipe(
                start = Offset(center.x, center.y * 1.3f),
                end = Offset(center.x, center.y * 0.5f),
                durationMillis = 500,
            )
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("dynamic-primary-tab").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("music-page-host").performTouchInput {
            swipe(
                start = Offset(center.x, center.y * 0.5f),
                end = Offset(center.x, center.y * 1.3f),
                durationMillis = 500,
            )
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("dynamic-primary-tabs").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("dynamic-primary-tab").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("dynamic-primary-tabs").assertIsDisplayed()
    }

    @Test
    fun bottomBarTriggersOnlyOnceUntilAllPointersAreReleased() {
        val track = Track(TrackId("direction-test"), "方向手势测试")
        setContent(
            state = MusicUiState(
                loading = false,
                tracks = (1..24).map { Track(TrackId("direction-$it"), "曲目 $it") },
            ),
            playerState = PlayerState(
                queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                currentIndex = 0,
                durationMs = 180_000,
            ),
        )
        val expandedPlayer = compose.onNodeWithTag("dynamic-mini-player").fetchSemanticsNode().boundsInRoot
        // Exceed touch slop, but stay well below the former 96dp collapse distance.
        val dragDistance = with(compose.density) { 24.dp.toPx() }
        compose.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, -dragDistance), delayMillis = 300)
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("dynamic-primary-tabs").assertDoesNotExist()
        compose.onNodeWithTag("dynamic-primary-tab").assertIsDisplayed()
        val collapsedPlayer = compose.onNodeWithTag("dynamic-mini-player").fetchSemanticsNode().boundsInRoot
        val collapsedTab = compose.onNodeWithTag("dynamic-primary-tab").fetchSemanticsNode().boundsInRoot
        assertEquals(collapsedTab.center.y, collapsedPlayer.center.y, 2f)
        assertTrue(collapsedPlayer.width < expandedPlayer.width)

        // Reversing repeatedly in the same gesture must not expand the bar.
        repeat(3) {
            compose.onRoot().performTouchInput {
                moveBy(Offset(0f, dragDistance), delayMillis = 300)
                moveBy(Offset(0f, -dragDistance), delayMillis = 300)
            }
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithTag("dynamic-primary-tabs").assertDoesNotExist()
            compose.onNodeWithTag("dynamic-primary-tab").assertIsDisplayed()
        }
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithTag("dynamic-primary-tab").assertIsDisplayed()

        // A new downward gesture can expand, even before the finger is released.
        compose.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, dragDistance), delayMillis = 300)
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("dynamic-primary-tab").assertDoesNotExist()
        compose.onNodeWithTag("dynamic-primary-tabs").assertIsDisplayed()
        val restoredPlayer = compose.onNodeWithTag("dynamic-mini-player").fetchSemanticsNode().boundsInRoot
        assertEquals(expandedPlayer.width, restoredPlayer.width, 2f)
        assertEquals(expandedPlayer.center.y, restoredPlayer.center.y, 2f)
        compose.onRoot().performTouchInput {
            moveBy(Offset(0f, -dragDistance), delayMillis = 300)
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("dynamic-primary-tab").assertDoesNotExist()

        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithTag("dynamic-primary-tabs").assertIsDisplayed()
    }

    @Test
    fun expandedMiniPlayerUsesReferenceInternalSpacing() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)

        val playerBounds = compose.onNodeWithTag("dynamic-mini-player").fetchSemanticsNode().boundsInRoot
        val coverBounds = compose.onNodeWithTag("dynamic-cover", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val playCenter = compose.onNodeWithContentDescription("播放或暂停", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center.x
        val nextCenter = compose.onNodeWithContentDescription("下一首", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center.x

        org.junit.Assert.assertEquals(
            playerBounds.height * 16f / 50f,
            coverBounds.left - playerBounds.left,
            2f,
        )
        org.junit.Assert.assertEquals(
            playerBounds.height * 48f / 50f,
            nextCenter - playCenter,
            2f,
        )
    }

    @Test
    fun selectingSearchMovesSelectionOutOfPrimaryTabs() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)

        compose.onNodeWithTag("liquid-bottom-tabs-indicator").assertIsDisplayed()
        compose.onNodeWithTag("dynamic-search").performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("liquid-bottom-tabs-indicator").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("dynamic-search").assertIsDisplayed()
    }

    @Test
    fun selectingSearchInCompactModeClearsThePrimarySelection() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)

        compose.onRoot().performTouchInput {
            swipe(
                start = Offset(center.x, center.y * 1.3f),
                end = Offset(center.x, center.y * 0.5f),
                durationMillis = 500,
            )
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("dynamic-primary-tab").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("dynamic-search").performClick()

        compose.onNodeWithTag("dynamic-search").assertIsSelected()
        compose.onNodeWithTag("dynamic-primary-tab").assertIsNotSelected()
    }

    @Test
    fun playerQueueUsesTheImmersivePlayerShell() {
        val first = Track(TrackId("track-one"), "第一首")
        val second = Track(TrackId("track-two"), "第二首")
        val player = PlayerState(
            queue = listOf(
                PlayableTrack(first, "https://music.invalid/one"),
                PlayableTrack(second, "https://music.invalid/two"),
            ),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)

        compose.onNodeWithText("第一首").performClick()
        compose.onNodeWithContentDescription("开启随机播放").assertDoesNotExist()
        compose.onNodeWithContentDescription("循环已关闭，点击切换为列表循环").assertDoesNotExist()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithText("待播队列", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("待播队列", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("第二首", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("仅保留当前", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("player-queue-entry").assertIsSelected()
        compose.onNodeWithTag("player-bottom-controls").assertIsDisplayed()

        compose.onNodeWithTag("player-queue-header").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("player-queue-header").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("player-queue-entry").assertIsNotSelected()

        compose.onNodeWithTag("player-queue-entry").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("player-queue-header").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("player-queue-entry").assertIsSelected()
    }

    @Test
    fun queueModeSelectsTracksAndDispatchesLocalReordering() {
        val tracks = (1..4).map { index ->
            Track(TrackId("queue-$index"), "队列曲目 $index")
        }
        val player = PlayerState(
            queue = tracks.map { PlayableTrack(it, "https://music.invalid/${it.id.value}") },
            currentIndex = 0,
            durationMs = 180_000,
        )
        var selected: Int? = null
        var moved: Pair<Int, Int>? = null
        setContent(
            playerState = player,
            onSkipToQueueItem = { selected = it },
            onMoveQueueItem = { from, to -> moved = from to to },
        )

        compose.onNodeWithText("队列曲目 1").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithText("待播队列", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("队列曲目 2", useUnmergedTree = true).performClick()
        org.junit.Assert.assertEquals(1, selected)

        compose.onNodeWithContentDescription("长按拖动排序：队列曲目 3").performTouchInput {
            down(center)
            advanceEventTime(600)
            moveTo(center + Offset(0f, -180f), delayMillis = 500)
            up()
        }
        org.junit.Assert.assertEquals(2 to 1, moved)
    }

    @Test
    fun shuffledQueueDisplaysAndEditsActualPlaybackOrder() {
        val tracks = (0..4).map { PlayableTrack(Track(TrackId("shuffle-$it"), "随机曲目 $it"), "https://music.invalid/$it") }
        val player = mutableStateOf(PlayerState(queue = tracks, currentIndex = 4, shuffleEnabled = true,
            playbackOrder = listOf(2, 4, 0, 3, 1), durationMs = 180_000))
        var selected: Int? = null
        val moves = mutableListOf<Pair<Int, Int>>()
        setContent(playerStateProvider = { player.value }, onSkipToQueueItem = { selected = it }, onMoveQueueItem = { from, to ->
            moves += from to to
            val order = player.value.playbackOrder.toMutableList()
            val source = order.indexOf(from)
            val destination = order.indexOf(to)
            order.add(destination, order.removeAt(source))
            player.value = player.value.copy(playbackOrder = order)
        })
        compose.onNodeWithText("随机曲目 4").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("player-queue-row-shuffle-0").assertIsDisplayed()
        compose.onNodeWithTag("player-queue-row-shuffle-2").assertDoesNotExist()
        compose.onNodeWithText("随机曲目 0", useUnmergedTree = true).performClick()
        assertEquals(0, selected)
        val first = compose.onNodeWithContentDescription("长按拖动排序：随机曲目 0").fetchSemanticsNode().boundsInRoot
        val third = compose.onNodeWithContentDescription("长按拖动排序：随机曲目 1").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            down(third.center)
            moveTo(first.center, delayMillis = 700)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf(1 to 0), moves)
        assertEquals(listOf(1, 0, 3), player.value.upcomingQueueIndices)
    }

    @Test
    fun queueSwipeRevealsRemovalWithoutPlayingAndClosesOnScroll() {
        val tracks = (0..15).map { PlayableTrack(Track(TrackId("swipe-$it"), "左滑曲目 $it"), "https://music.invalid/$it") }
        val player = mutableStateOf(PlayerState(queue = tracks, currentIndex = 0, durationMs = 180_000))
        var selected: Int? = null
        var removed: Int? = null
        setContent(playerStateProvider = { player.value }, onSkipToQueueItem = { selected = it }, onRemoveFromQueue = { index ->
            removed = index
            player.value = player.value.copy(queue = player.value.queue.filterIndexed { i, _ -> i != index })
        })
        compose.onNodeWithText("左滑曲目 0").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("player-queue-row-swipe-1").performTouchInput {
            swipe(start = Offset(width * .7f, center.y), end = Offset(width * .2f, center.y), durationMillis = 450)
        }
        compose.waitForIdle()
        assertEquals(null, selected)
        compose.onNodeWithContentDescription("从待播队列移除：左滑曲目 1").assertIsDisplayed()
        captureQueueScreenshot("queue-swipe")
        compose.onNodeWithContentDescription("从待播队列移除：左滑曲目 1").performClick()
        compose.waitForIdle()
        assertEquals(1, removed)
        assertEquals(tracks.first().queueEntryId, player.value.current?.queueEntryId)
        compose.onNodeWithTag("player-queue-row-swipe-1").assertDoesNotExist()
        compose.onNodeWithTag("player-queue-row-swipe-2").performTouchInput {
            swipe(start = Offset(width * .7f, center.y), end = Offset(width * .2f, center.y), durationMillis = 450)
        }
        compose.onNodeWithContentDescription("从待播队列移除：左滑曲目 2").assertIsDisplayed()
        compose.onNodeWithTag("player-queue-list").performTouchInput {
            swipe(start = center + Offset(0f, 80f), end = center - Offset(0f, 80f), durationMillis = 800)
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("从待播队列移除：左滑曲目 2").assertDoesNotExist()
    }

    @Test
    fun duplicateQueueEntriesRetainIdentityAfterDraggingAndRemoval() {
        val current = PlayableTrack(Track(TrackId("duplicate-current"), "重复队列测试"), "https://music.invalid/current")
        val first = PlayableTrack(Track(TrackId("duplicate"), "重复第一项"), "https://music.invalid/duplicate")
        val second = first.copy(track = first.track.copy(title = "重复第二项"), queueEntryId = "second-occurrence")
        val player = mutableStateOf(PlayerState(queue = listOf(current, first, second), currentIndex = 0, durationMs = 180_000))
        var removedId: String? = null
        setContent(playerStateProvider = { player.value }, onMoveQueueItem = { from, to ->
            player.value = player.value.copy(queue = player.value.queue.toMutableList().also { it.add(to, it.removeAt(from)) })
        }, onRemoveFromQueue = { index ->
            removedId = player.value.queue[index].queueEntryId
            player.value = player.value.copy(queue = player.value.queue.filterIndexed { i, _ -> i != index })
        })
        compose.onNodeWithText("重复队列测试").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitForIdle()
        val firstHandle = compose.onNodeWithContentDescription("长按拖动排序：重复第一项").fetchSemanticsNode().boundsInRoot
        val secondHandle = compose.onNodeWithContentDescription("长按拖动排序：重复第二项").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            down(secondHandle.center)
            moveTo(firstHandle.center, delayMillis = 700)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf(current.queueEntryId, second.queueEntryId, first.queueEntryId), player.value.queue.map { it.queueEntryId })
        val row = compose.onNodeWithText("重复第二项", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            swipe(start = row.center, end = row.center - Offset(180f, 0f), durationMillis = 450)
        }
        compose.onNodeWithContentDescription("从待播队列移除：重复第二项").performClick()
        compose.waitForIdle()
        assertEquals(second.queueEntryId, removedId)
        assertEquals(listOf(current.queueEntryId, first.queueEntryId), player.value.queue.map { it.queueEntryId })
    }

    @Test
    fun queueModeRevealsPlaybackHistoryOnlyAfterPullingDown() {
        val historyTrack = Track(TrackId("history-track"), "已播放歌曲")
        val currentTrack = Track(TrackId("history-current"), "当前歌曲")
        val upcomingTrack = Track(TrackId("history-upcoming"), "待播歌曲")
        val player = PlayerState(
            queue = listOf(
                PlayableTrack(historyTrack, "https://music.invalid/history"),
                PlayableTrack(currentTrack, "https://music.invalid/current"),
                PlayableTrack(upcomingTrack, "https://music.invalid/upcoming"),
            ),
            playbackHistory = listOf(PlayableTrack(historyTrack, "https://music.invalid/history")),
            currentIndex = 1,
            durationMs = 180_000,
        )
        var selectedHistory: Int? = null
        var clearRequests = 0
        setContent(
            playerState = player,
            onSkipToHistoryItem = { selectedHistory = it },
            onClearPlaybackHistory = { clearRequests += 1 },
        )

        compose.onNodeWithText("当前歌曲").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.onNodeWithTag("player-playback-history").assertIsNotDisplayed()
        compose.onNodeWithTag("player-queue-row-history-current").assertDoesNotExist()
        compose.onNodeWithTag("player-queue-row-history-upcoming").assertIsDisplayed()

        compose.onNodeWithTag("player-queue-list").performTouchInput {
            swipe(
                start = center - Offset(0f, 160f),
                end = center + Offset(0f, 260f),
                durationMillis = 600,
            )
        }
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("player-playback-history").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("播放记录").assertIsDisplayed()
        compose.onNodeWithContentDescription("播放记录：已播放歌曲").performClick()
        org.junit.Assert.assertEquals(0, selectedHistory)
        compose.onNodeWithContentDescription("清空播放记录").performClick()
        org.junit.Assert.assertEquals(1, clearRequests)
    }

    @Test
    fun queueModeControlsStayPinnedWhileTheQueueScrolls() {
        val tracks = (1..24).map { index ->
            Track(TrackId("pinned-queue-$index"), "固定控制测试 $index")
        }
        val player = PlayerState(
            queue = tracks.map { PlayableTrack(it, "https://music.invalid/${it.id.value}") },
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)

        compose.onNodeWithText("固定控制测试 1").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("player-queue-mode-controls").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("player-queue-list").performTouchInput {
            swipe(start = center + Offset(0f, 200f), end = center - Offset(0f, 200f), durationMillis = 800)
        }
        compose.waitForIdle()
        val titleTop = compose.onNodeWithTag("player-queue-title").fetchSemanticsNode().boundsInRoot.top
        val controlsTop = compose.onNodeWithTag("player-queue-mode-controls").fetchSemanticsNode().boundsInRoot.top
        val viewportTop = compose.onNodeWithTag("player-queue-list").fetchSemanticsNode().boundsInRoot.top
        assertEquals(viewportTop, controlsTop, 1f)
        compose.onNodeWithTag("player-queue-list").performTouchInput {
            swipe(start = center + Offset(0f, 120f), end = center - Offset(0f, 120f), durationMillis = 800)
        }
        compose.waitForIdle()
        val scrolledControlsTop = compose.onNodeWithTag("player-queue-mode-controls").fetchSemanticsNode().boundsInRoot.top
        assertEquals(controlsTop, scrolledControlsTop, 1f)
        assertEquals(titleTop, compose.onNodeWithTag("player-queue-title").fetchSemanticsNode().boundsInRoot.top, 1f)
        captureQueueScreenshot("queue-pinned")
    }

    @Test
    fun historyPullMovesRealCoverWithHeaderAndCanReachEveryHistoryRow() {
        val tracks = (1..12).map { PlayableTrack(Track(TrackId("continuous-$it"), "连续滚动 $it"), "https://music.invalid/$it") }
        val player = PlayerState(queue = tracks, playbackHistory = tracks.take(8), currentIndex = 8, durationMs = 180_000)
        setContent(playerState = player)
        compose.onNodeWithText("连续滚动 9").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitForIdle()
        val headerBefore = compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top
        val coverBefore = compose.onNodeWithTag("player-queue-cover", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("player-queue-list").performTouchInput {
            down(center)
            repeat(6) { moveBy(Offset(0f, 15f), delayMillis = 150) }
        }
        compose.waitForIdle()
        val headerAfter = compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top
        val coverAfter = compose.onNodeWithTag("player-queue-cover", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        assertTrue("下拉应连续移动歌曲栏：before=$headerBefore after=$headerAfter", headerAfter > headerBefore + 20f)
        assertEquals(headerAfter - headerBefore, coverAfter - coverBefore, 1f)
        compose.onNodeWithTag("player-queue-list").performTouchInput {
            advanceEventTime(300)
            up()
        }
        compose.waitForIdle()
        assertEquals("短拉释放后回到当前歌曲", headerBefore,
            compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top, 2f)
        compose.onNodeWithTag("player-queue-list")
            .performScrollToNode(hasContentDescription("播放记录：连续滚动 1"))
        compose.onNodeWithContentDescription("播放记录：连续滚动 1").assertIsDisplayed()
    }


    @Test
    fun singleHistorySnapsBetweenNaturalSectionsWithoutBlankSpace() {
        val tracks = (1..12).map {
            PlayableTrack(Track(TrackId("snap-$it"), "停靠歌曲 $it"), "https://music.invalid/$it")
        }
        val player = mutableStateOf(PlayerState(queue = tracks, playbackHistory = tracks.take(1), currentIndex = 1))
        setContent(
            windowSize = { androidx.compose.ui.unit.DpSize(390.dp, (390f * 20f / 9f).dp) },
            playerStateProvider = { player.value },
            onClearPlaybackHistory = { player.value = player.value.copy(playbackHistory = emptyList()) },
        )
        compose.onNodeWithText("停靠歌曲 2").performClick()
        compose.onNodeWithTag("player-queue-entry").performClick()
        val viewport = compose.onNodeWithTag("player-queue-list").fetchSemanticsNode().boundsInRoot
        val scale = compose.onNodeWithTag("player-morph-overlay").fetchSemanticsNode().boundsInRoot.width / 390f
        val currentTop = compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top
        fun drag(distanceDp: Float) {
            compose.onNodeWithTag("player-queue-list").performTouchInput {
                val from = Offset(center.x, height - 32f * scale)
                swipe(from, from + Offset(0f, distanceDp * scale), durationMillis = 1000)
            }
            compose.waitForIdle()
        }
        drag(35f)
        assertEquals(currentTop, compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top, 2f)
        drag(115f)
        assertEquals("一条历史完整展开后仍可停住", viewport.top,
            compose.onNodeWithTag("player-playback-history").fetchSemanticsNode().boundsInRoot.top, 2f)
        assertEquals("历史按实际内容高度展开，不补满一屏", currentTop + 144f * scale,
            compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top, 3f)
        captureQueueScreenshot("queue-snap-single-history")
        drag(-115f)
        assertEquals(currentTop, compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top, 2f)
        drag(-60f)
        assertEquals("当前歌曲收起后模式栏停靠顶部", viewport.top,
            compose.onNodeWithTag("player-queue-mode-controls").fetchSemanticsNode().boundsInRoot.top, 2f)
        compose.onNodeWithText("播放记录").assertDoesNotExist()
        captureQueueScreenshot("queue-snap-modes")
        drag(195f)
        compose.onNodeWithContentDescription("清空播放记录").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("播放记录").assertDoesNotExist()
        assertEquals("清空历史后当前歌曲回到有效边界", currentTop,
            compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top, 2f)
    }

    @Test
    fun touchingDuringQueueSnapImmediatelyReturnsControlToTheFinger() {
        val tracks = (1..12).map {
            PlayableTrack(Track(TrackId("interrupt-snap-$it"), "打断停靠 $it"), "https://music.invalid/$it")
        }
        setContent(
            windowSize = { androidx.compose.ui.unit.DpSize(390.dp, (390f * 20f / 9f).dp) },
            playerState = PlayerState(queue = tracks, playbackHistory = tracks.take(1), currentIndex = 1),
        )
        compose.onNodeWithText("打断停靠 2").performClick()
        compose.onNodeWithTag("player-queue-entry").performClick()
        val viewport = compose.onNodeWithTag("player-queue-list").fetchSemanticsNode().boundsInRoot
        val scale = compose.onNodeWithTag("player-morph-overlay").fetchSemanticsNode().boundsInRoot.width / 390f
        val start = Offset(viewport.center.x, viewport.bottom - 32f * scale)
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput {
            down(start)
            repeat(8) { moveBy(Offset(0f, 5f * scale), delayMillis = 30) }
            advanceEventTime(200)
            up()
        }
        compose.mainClock.advanceTimeBy(48)
        compose.onRoot().performTouchInput {
            down(start)
            repeat(8) { moveBy(Offset(0f, 8f * scale), delayMillis = 30) }
        }
        compose.mainClock.advanceTimeBy(32)
        val heldTop = compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top
        assertTrue("重新触摸后跟随新手势", heldTop > viewport.top + 40f * scale)
        compose.mainClock.advanceTimeBy(600)
        assertEquals("按住时不能继续旧的吸附动画", heldTop,
            compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top, 2f)
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("player-morph-overlay").assertIsDisplayed()
    }

    @Test
    fun longHistoryStaysScrollableWithItsTitlePinned() {
        val tracks = (1..24).map {
            PlayableTrack(Track(TrackId("snap-history-$it"), "历史停靠 $it"), "https://music.invalid/$it")
        }
        setContent(
            windowSize = { androidx.compose.ui.unit.DpSize(390.dp, (390f * 20f / 9f).dp) },
            playerState = PlayerState(queue = tracks, playbackHistory = tracks.take(12), currentIndex = 12),
        )
        compose.onNodeWithText("历史停靠 13").performClick()
        compose.onNodeWithTag("player-queue-entry").performClick()
        val viewport = compose.onNodeWithTag("player-queue-list").fetchSemanticsNode().boundsInRoot
        val scale = compose.onNodeWithTag("player-morph-overlay").fetchSemanticsNode().boundsInRoot.width / 390f
        compose.onNodeWithTag("player-queue-list").performTouchInput {
            val from = Offset(center.x, height - 32f * scale)
            swipe(from, from + Offset(0f, 280f * scale), durationMillis = 1000)
        }
        compose.waitForIdle()
        assertTrue("历史深处不强制回当前歌曲",
            compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top > viewport.top + 200f * scale)
        assertEquals(viewport.top,
            compose.onNodeWithTag("player-playback-history").fetchSemanticsNode().boundsInRoot.top, 2f)
        captureQueueScreenshot("queue-snap-history-pinned")
        compose.onNodeWithTag("player-queue-list").performScrollToNode(hasContentDescription("播放记录：历史停靠 1"))
        compose.onNodeWithContentDescription("播放记录：历史停靠 1").assertIsDisplayed()
        captureQueueScreenshot("queue-snap-long-history")
    }

    @Test
    fun draggingNearBottomScrollsBeyondInitiallyVisibleRows() {
        val tracks = (1..24).map { PlayableTrack(Track(TrackId("edge-$it"), "边缘拖动 $it"), "https://music.invalid/$it") }
        val player = mutableStateOf(PlayerState(queue = tracks, currentIndex = 0, durationMs = 180_000))
        val moves = mutableListOf<Pair<Int, Int>>()
        setContent(playerStateProvider = { player.value }, onMoveQueueItem = { from, to ->
            moves += from to to
            player.value = player.value.copy(queue = player.value.queue.toMutableList().also { it.add(to, it.removeAt(from)) })
        })
        compose.onNodeWithText("边缘拖动 1").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitForIdle()
        val handle = compose.onNodeWithContentDescription("长按拖动排序：边缘拖动 2").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("player-queue-list").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput {
            down(handle.center)
            moveTo(Offset(handle.center.x, viewport.bottom - 12f), delayMillis = 700)
        }
        compose.mainClock.advanceTimeBy(2400)
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("持续拖在可视底边应滚出屏外歌曲并继续重排", moves.any { it.second >= 8 })
        assertEquals("edge-2", player.value.queue[moves.last().second].track.id.value)
    }

    @Test
    fun queueKeepsCurrentAnchorAcrossTrackChangesAndResetsForNewSession() {
        val tracks = (1..12).map { PlayableTrack(Track(TrackId("anchor-$it"), "滚动锚点 $it"), "https://music.invalid/$it") }
        val player = mutableStateOf(PlayerState(queue = tracks, currentIndex = 0, durationMs = 180_000))
        setContent(playerStateProvider = { player.value })
        compose.onNodeWithText("滚动锚点 1").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitForIdle()
        val initialTop = compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { player.value = player.value.copy(currentIndex = 1, playbackHistory = tracks.take(1)) }
        compose.waitForIdle()
        assertEquals(initialTop, compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithContentDescription("播放记录：滚动锚点 1").assertIsNotDisplayed()
        compose.onNodeWithTag("player-queue-row-anchor-3").assertIsDisplayed()
        val revealDistance = with(compose.density) { 120.dp.toPx() }
        compose.onNodeWithTag("player-queue-list").performTouchInput {
            swipe(start = center, end = center + Offset(0f, revealDistance), durationMillis = 800)
        }
        compose.onNodeWithContentDescription("播放记录：滚动锚点 1").assertIsDisplayed()
        compose.runOnIdle {
            player.value = PlayerState(queue = tracks.reversed(), currentIndex = 0, playbackSessionId = 1, durationMs = 180_000)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("player-playback-history").assertDoesNotExist()
        assertEquals(initialTop, compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test
    fun emptyUpcomingQueueStillStartsWithHistoryOutsideViewport() {
        val tracks = (1..9).map { PlayableTrack(Track(TrackId("empty-$it"), "空待播 $it"), "https://music.invalid/$it") }
        setContent(playerState = PlayerState(queue = tracks, playbackHistory = tracks.take(8), currentIndex = 8))
        compose.onNodeWithText("空待播 9").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitForIdle()
        assertEquals(
            compose.onNodeWithTag("player-queue-list").fetchSemanticsNode().boundsInRoot.top,
            compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top,
            1f,
        )
        compose.onNodeWithContentDescription("播放记录：空待播 8").assertIsNotDisplayed()
    }

    @Test
    fun queueLastRowScrollsAbovePersistentPlaybackControls() {
        val tracks = (1..24).map { index ->
            Track(TrackId("queue-bottom-$index"), "末项可见测试 $index")
        }
        val player = PlayerState(
            queue = tracks.map { PlayableTrack(it, "https://music.invalid/${it.id.value}") },
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)

        compose.onNodeWithText("末项可见测试 1").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        val finalRowTag = "player-queue-row-queue-bottom-24"
        repeat(8) {
            compose.onNodeWithTag("player-queue-list").performTouchInput {
                swipe(
                    start = center + Offset(0f, 220f),
                    end = center - Offset(0f, 260f),
                    durationMillis = 500,
                )
            }
        }
        compose.waitForIdle()

        val finalRow = compose.onNodeWithTag(finalRowTag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("player-bottom-controls", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "队列末项不能落在常驻播放控制区域下方",
            finalRow.bottom <= controls.top,
        )
    }

    @Test
    fun closingQueueFromLyricsReturnsToDefaultPlayerWithoutShowingLyricsAgain() {
        val track = Track(TrackId("queue-return-track"), "队列返回测试")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/queue-return")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)

        compose.onNodeWithText("队列返回测试").performClick()
        compose.onNodeWithContentDescription("展开完整歌词").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("player-lyrics-header").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("player-queue-header").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("player-queue-header").fetchSemanticsNodes().isEmpty()
        }

        compose.onNodeWithTag("player-lyrics-header").assertDoesNotExist()
        compose.onNodeWithTag("player-track-metadata").assertIsDisplayed()
    }

    @Test
    fun switchingTracksKeepsExpandedCoverBoundsOnEveryFrame() {
        val tracks = listOf("第一首", "第二首", "第三首").mapIndexed { index, title ->
            PlayableTrack(Track(TrackId("switch-$index"), title), "https://music.invalid/$index")
        }
        val player = mutableStateOf(PlayerState(queue = tracks, currentIndex = 0, durationMs = 180_000))
        setContent(playerStateProvider = { player.value },
            onNext = { player.value = player.value.copy(currentIndex = player.value.currentIndex + 1, positionMs = 0) },
            onPrevious = { player.value = player.value.copy(currentIndex = player.value.currentIndex - 1, positionMs = 0) })
        compose.onNodeWithTag("dynamic-mini-player").performClick()
        val original = compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        listOf("下一首", "下一首", "上一首").forEach { action ->
            compose.onNodeWithContentDescription(action).performClick()
            repeat(12) { frame ->
                compose.mainClock.advanceTimeByFrame()
                val cover = compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot
                assertEquals("$action 第 $frame 帧不应退回迷你封面", original.width, cover.width, 2f)
                assertEquals("$action 第 $frame 帧应保持大封面位置", original.top, cover.top, 2f)
            }
        }
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun notificationOpenWaitsForRestoredTrackAndConsumesTheRequestOnce() {
        val player = mutableStateOf(PlayerState())
        val request = mutableStateOf(true)
        var consumed = 0
        setContent(playerStateProvider = { player.value }, openPlayerRequested = { request.value },
            onPlayerOpenRequestConsumed = { consumed++; request.value = false })
        compose.runOnIdle { assertEquals(0, consumed) }
        compose.runOnIdle {
            player.value = PlayerState(queue = listOf(
                PlayableTrack(Track(TrackId("notification"), "通知恢复"), "https://music.invalid/notification"),
            ), currentIndex = 0)
        }
        compose.onNodeWithTag("player-morph-cover").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, consumed) }
        compose.runOnIdle { player.value = player.value.copy(positionMs = 1_000) }
        compose.runOnIdle { assertEquals(1, consumed) }
    }

    @Test
    fun playbackCoverActuallyOvershootsBeforeSettling() {
        val track = PlayableTrack(Track(TrackId("bounce"), "回弹测试"), "https://music.invalid/bounce")
        val player = mutableStateOf(PlayerState(queue = listOf(track), currentIndex = 0, playbackStatus = PlaybackStatus.Playing))
        setContent(playerStateProvider = { player.value })
        compose.onNodeWithTag("dynamic-mini-player").performClick()
        fun width() = compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot.width
        val expanded = width()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { player.value = player.value.copy(playbackStatus = PlaybackStatus.Paused) }
        val shrinking = List(60) { compose.mainClock.advanceTimeByFrame(); width() }
        compose.runOnIdle { player.value = player.value.copy(playbackStatus = PlaybackStatus.Playing) }
        val growing = List(60) { compose.mainClock.advanceTimeByFrame(); width() }
        compose.mainClock.autoAdvance = true
        org.junit.Assert.assertTrue(
            "缩小时应越过目标后回弹: minimum=${shrinking.min()}, target=${expanded * 0.73f}",
            shrinking.min() < expanded * 0.73f - expanded * 0.01f,
        )
        org.junit.Assert.assertTrue(
            "放大时应越过目标后回弹: maximum=${growing.max()}, target=$expanded",
            growing.max() > expanded * 1.01f,
        )
        assertEquals(expanded * 0.73f, shrinking.last(), 1f)
        assertEquals(expanded, growing.last(), 1f)
    }

    @Test
    fun playbackCoverScalesSmoothlyWithoutMovingControlsAndCanReverse() {
        val track = PlayableTrack(Track(TrackId("scale"), "缩放测试"), "https://music.invalid/scale")
        val player = mutableStateOf(PlayerState(queue = listOf(track), currentIndex = 0, playbackStatus = PlaybackStatus.Playing))
        setContent(playerStateProvider = { player.value })
        compose.onNodeWithTag("dynamic-mini-player").performClick()
        fun cover() = compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot
        val expanded = cover()
        val controls = compose.onNodeWithTag("player-transport-controls").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { player.value = player.value.copy(playbackStatus = PlaybackStatus.Paused) }
        compose.mainClock.advanceTimeBy(128)
        val shrinking = cover()
        org.junit.Assert.assertTrue(shrinking.width < expanded.width - 2f)
        org.junit.Assert.assertTrue(shrinking.width > expanded.width * 0.73f + 2f)
        assertEquals(expanded.center.x, shrinking.center.x, 2f)
        assertEquals(expanded.center.y, shrinking.center.y, 2f)
        compose.runOnIdle { player.value = player.value.copy(playbackStatus = PlaybackStatus.Playing) }
        compose.mainClock.advanceTimeByFrame()
        org.junit.Assert.assertTrue("反转时不能直接跳到终点", cover().width < expanded.width - 2f)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(expanded.width, cover().width, 2f)
        compose.runOnIdle { player.value = player.value.copy(playbackStatus = PlaybackStatus.Paused) }
        compose.mainClock.advanceTimeBy(1_000)
        val paused = cover()
        assertEquals(expanded.width * 0.73f, paused.width, 2f)
        assertEquals(expanded.center.x, paused.center.x, 2f)
        assertEquals(expanded.center.y, paused.center.y, 2f)
        assertEquals(controls, compose.onNodeWithTag("player-transport-controls").fetchSemanticsNode().boundsInRoot)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        val compact = cover()
        // Playing lyrics intentionally keep a frame clock running.
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { player.value = player.value.copy(playbackStatus = PlaybackStatus.Playing) }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(compact, cover())
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.mainClock.autoAdvance = true
        assertEquals(expanded.width, cover().width, 2f)
    }

    @Test
    fun nowPlayingUsesArtworkLedVerticalControlHierarchy() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
            playbackStatus = PlaybackStatus.Playing,
        )
        setContent(playerState = player)

        compose.onNodeWithText("测试曲目").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot.height > 500f
        }

        val cover = compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot
        val metadata = compose.onNodeWithTag("player-track-metadata").fetchSemanticsNode().boundsInRoot
        val progress = compose.onNodeWithTag("player-playback-progress").fetchSemanticsNode().boundsInRoot
        val transport = compose.onNodeWithTag("player-transport-controls").fetchSemanticsNode().boundsInRoot
        val volume = compose.onNodeWithTag("player-volume-control").fetchSemanticsNode().boundsInRoot
        val utilities = compose.onNodeWithTag("player-bottom-utilities").fetchSemanticsNode().boundsInRoot

        org.junit.Assert.assertTrue(metadata.top >= cover.bottom)
        org.junit.Assert.assertEquals(cover.left, metadata.left, 2f)
        org.junit.Assert.assertEquals(cover.right, metadata.right, 2f)
        org.junit.Assert.assertEquals(cover.left, progress.left, 2f)
        org.junit.Assert.assertEquals(cover.right, progress.right, 2f)
        val metadataToProgressGap = progress.top - metadata.bottom
        val minimumMetadataToProgressGap = with(compose.density) { 24.dp.toPx() }
        org.junit.Assert.assertTrue(
            "歌曲信息与进度条间距应至少为 24dp，实际为 ${metadataToProgressGap}px",
            metadataToProgressGap >= minimumMetadataToProgressGap,
        )
        org.junit.Assert.assertTrue(transport.top >= progress.bottom)
        org.junit.Assert.assertTrue(volume.top >= transport.bottom)
        org.junit.Assert.assertTrue(utilities.top >= volume.bottom)
        compose.onNodeWithTag("player-artwork-background").assertIsDisplayed()
        compose.onNodeWithTag("player-favorite-action").assertIsDisplayed()
        compose.onNodeWithTag("player-more-action").assertIsDisplayed()
    }

    @Test
    fun untimedLyricsCanExpandIntoAFullScreenPage() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = listOf(LyricLine(text = "第一句"), LyricLine(text = "第二句"), LyricLine(text = "第三句")),
        )
        setContent(state = state, playerState = player)

        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithContentDescription("展开完整歌词").performClick()

        compose.onNodeWithTag("player-lyrics-entry").assertIsSelected()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodes(hasText("第一句")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("lyrics-list").assertIsDisplayed()
        compose.onNodeWithText("第一句").assertIsDisplayed()
        compose.onNodeWithText("第三句").assertIsDisplayed()
        compose.onNodeWithText("首页").assertDoesNotExist()
    }

    @Test
    fun lyricsUsesBottomEntriesAndTogglesBackToTheLargeCover() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = listOf(LyricLine(text = "第一句"), LyricLine(text = "第二句")),
        )
        setContent(state = state, playerState = player)

        compose.onNodeWithText("测试曲目").performClick()
        val largeCover = compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot
        val defaultProgress = compose.onNodeWithTag("player-playback-progress").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("player-center-entry-placeholder").assertIsDisplayed()
        compose.onNodeWithTag("player-queue-entry").assertIsDisplayed()

        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.onNodeWithTag("player-lyrics-entry").assertIsSelected()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot.height < largeCover.height * 0.4f
        }
        compose.onNodeWithTag("player-lyrics-entry").assertIsSelected()
        compose.onNodeWithTag("player-lyrics-header-blur").assertIsDisplayed()
        compose.onNodeWithTag("player-bottom-controls-blur").assertIsDisplayed()
        val header = compose.onNodeWithTag("player-lyrics-header").fetchSemanticsNode().boundsInRoot
        val headerBlur = compose.onNodeWithTag("player-lyrics-header-blur").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("player-bottom-controls").fetchSemanticsNode().boundsInRoot
        val controlsBlur = compose.onNodeWithTag("player-bottom-controls-blur").fetchSemanticsNode().boundsInRoot
        val lyricsProgress = compose.onNodeWithTag("player-playback-progress").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(
            "默认状态与歌词状态的进度控制区应保持相同纵向位置",
            defaultProgress.top,
            lyricsProgress.top,
            2f,
        )
        org.junit.Assert.assertTrue(headerBlur.bottom > header.bottom)
        org.junit.Assert.assertTrue(controlsBlur.top < controls.top)
        org.junit.Assert.assertTrue(controlsBlur.bottom >= controls.bottom)

        compose.onNodeWithTag("player-lyrics-header").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot.height > largeCover.height * 0.9f
        }
        compose.onNodeWithTag("player-lyrics-entry").assertIsNotSelected()
    }

    @Test
    fun tappingTheLyricsCoverReturnsToTheLargeCover() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = listOf(LyricLine(text = "第一句"), LyricLine(text = "第二句")),
        )
        setContent(state = state, playerState = player)

        compose.onNodeWithText("测试曲目").performClick()
        val largeCover = compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot.height < largeCover.height * 0.4f
        }

        compose.onNodeWithTag("player-morph-cover").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot.height > largeCover.height * 0.9f
        }
        compose.onNodeWithTag("player-lyrics-entry").assertIsNotSelected()
    }

    @Test
    fun lyricsHeaderMoreActionUsesTheTrackCommandSheet() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = listOf(LyricLine(text = "第一句"), LyricLine(text = "第二句")),
        )
        setContent(state = state, playerState = player)

        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onNodeWithTag("player-lyrics-more-action", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.width > 0f
        }

        compose.onNodeWithTag("player-lyrics-more-action", useUnmergedTree = true).performClick()
        compose.onNodeWithText("下一首播放").assertIsDisplayed()
    }

    @Test
    fun finalTimedLyricCanRemainCenteredWithTrailingSpace() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            positionMs = 175_000,
            durationMs = 180_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = listOf(
                LyricLine(timeMs = 0, text = "开头歌词"),
                LyricLine(timeMs = 60_000, text = "中间歌词"),
                LyricLine(timeMs = 120_000, text = "末尾歌词"),
            ),
        )
        setContent(state = state, playerState = player)

        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithContentDescription("展开完整歌词").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("lyrics-line-2")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag("lyrics-list").assertIsDisplayed()
        compose.onNodeWithTag("lyrics-line-2").assertIsDisplayed()
    }

    @Test
    fun autoFollowingLyricsBlurByDistanceAndManualScrollClearsBlur() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            positionMs = 80_000,
            durationMs = 180_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = (0..8).map { index ->
                LyricLine(timeMs = index * 20_000L, text = "第 ${index + 1} 句歌词")
            },
        )
        setContent(state = state, playerState = player)

        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithContentDescription("展开完整歌词").performClick()

        fun blurNodes() = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(LyricBlurRadiusKey),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()

        compose.waitUntil(timeoutMillis = 3_000) {
            blurNodes().count { it.config[LyricBlurRadiusKey] > 0.1f } >= 2
        }
        val activeNode = compose.onNodeWithTag("lyrics-line-4", useUnmergedTree = true)
            .fetchSemanticsNode()
        assertEquals(0f, activeNode.config[LyricBlurRadiusKey], 0.05f)

        val activeCenterY = activeNode.boundsInRoot.center.y
        val blurredNodes = blurNodes()
            .filter { it.config[LyricBlurRadiusKey] > 0.1f }
            .sortedBy { kotlin.math.abs(it.boundsInRoot.center.y - activeCenterY) }
        assertTrue("自动跟随时应有至少两行非高亮歌词产生模糊", blurredNodes.size >= 2)
        assertTrue(
            "距离高亮行越远，模糊半径应越大",
            blurredNodes.last().config[LyricBlurRadiusKey] >
                blurredNodes.first().config[LyricBlurRadiusKey],
        )

        compose.onNodeWithTag("lyrics-line-4", useUnmergedTree = true).performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, -360f),
                durationMillis = 400,
            )
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            val nodes = blurNodes()
            nodes.isNotEmpty() && nodes.all { it.config[LyricBlurRadiusKey] < 0.05f }
        }
    }

    @Test
    fun automaticLyricAdvanceMovesContinuouslyWithoutSnappingToTheTop() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = mutableStateOf(
            PlayerState(
                queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                currentIndex = 0,
                positionMs = 80_000,
                durationMs = 180_000,
            ),
        )
        val state = MusicUiState(
            loading = false,
            lyrics = (0..8).map { index ->
                LyricLine(timeMs = index * 20_000L, text = "第 ${index + 1} 句歌词")
            },
        )
        setContent(state = state, playerStateProvider = { player.value })

        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithContentDescription("展开完整歌词").performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("lyrics-line-5", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        val targetCenterY = compose.onNodeWithTag("lyrics-line-4", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .y
        val startCenterY = compose.onNodeWithTag("lyrics-line-5", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .y
        assertTrue("下一行在过行前应位于当前行下方", startCenterY > targetCenterY + 20f)

        val sampledCenters = mutableListOf<Float>()
        val nonTransitioningBlurSamples = mutableListOf<Float>()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { player.value = player.value.copy(positionMs = 100_000) }
            repeat(24) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithTag("lyrics-line-5", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .firstOrNull()
                    ?.boundsInRoot
                    ?.center
                    ?.y
                    ?.let(sampledCenters::add)
                (0..8)
                    .filterNot { it == 4 || it == 5 }
                    .flatMap { index ->
                        compose.onAllNodesWithTag("lyrics-line-$index", useUnmergedTree = true)
                            .fetchSemanticsNodes()
                    }
                    .mapTo(nonTransitioningBlurSamples) { node ->
                        node.config[LyricBlurRadiusKey]
                    }
            }
            compose.mainClock.advanceTimeBy(160)
        } finally {
            compose.mainClock.autoAdvance = true
        }

        val intermediateCenters = sampledCenters.filter { centerY ->
            centerY > targetCenterY + 3f && centerY < startCenterY - 3f
        }
        assertTrue(
            "歌词过行应经历多个中间位置，而不是瞬移",
            intermediateCenters.distinctBy { (it / 2f).toInt() }.size >= 3,
        )
        assertTrue(
            "新高亮行滚动过程中不应先闪到目标位置上方",
            sampledCenters.none { it < targetCenterY - 12f },
        )
        assertTrue(
            "过行期间，与新旧高亮无关的可见歌词不应瞬间变清晰",
            nonTransitioningBlurSamples.isNotEmpty() &&
                nonTransitioningBlurSamples.all { it > 0.05f },
        )
        val finalCenterY = compose.onNodeWithTag("lyrics-line-5", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .y
        assertEquals(targetCenterY, finalCenterY, 4f)
    }

    @Test
    fun playbackProgressSeekRestoresFollowingAndMovesToTheRequestedLyric() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            positionMs = 40_000,
            durationMs = 200_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = (0..9).map { index ->
                LyricLine(timeMs = index * 20_000L, text = "第 ${index + 1} 句歌词")
            },
        )
        var requestedPosition: Long? = null
        setContent(
            state = state,
            playerState = player,
            onSeek = { requestedPosition = it },
        )

        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithContentDescription("展开完整歌词").performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("lyrics-line-2", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag("lyrics-line-2", useUnmergedTree = true).performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, -360f),
                durationMillis = 400,
            )
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodes(
                SemanticsMatcher.keyIsDefined(LyricBlurRadiusKey),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().all { it.config[LyricBlurRadiusKey] < 0.05f }
        }

        compose.onNodeWithTag("player-playback-progress", useUnmergedTree = true)
            .performTouchInput {
                click(Offset(center.x * 1.64f, center.y))
            }

        compose.waitUntil(timeoutMillis = 4_000) {
            requestedPosition != null &&
                compose.onAllNodesWithTag("lyrics-line-8", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
        }
        assertTrue("进度条应请求跳转到靠后的播放位置", requestedPosition!! >= 150_000L)
        val requestedLine = compose.onNodeWithTag("lyrics-line-8", useUnmergedTree = true)
            .fetchSemanticsNode()
        assertTrue(
            "进度条跳转后的歌词应进入屏幕焦点区域",
            requestedLine.boundsInRoot.center.y in 700f..1_150f,
        )
        assertEquals(0f, requestedLine.config[LyricBlurRadiusKey], 0.05f)
        assertTrue(
            "进度条跳转后应立即恢复自动跟随模糊",
            compose.onAllNodes(
                SemanticsMatcher.keyIsDefined(LyricBlurRadiusKey),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().any { it.config[LyricBlurRadiusKey] > 0.1f },
        )
    }

    @Test
    fun manualLyricsScrollReturnsToTheCurrentLineAfterEightSeconds() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            positionMs = 80_000,
            durationMs = 180_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = (0..8).map { index ->
                LyricLine(timeMs = index * 20_000L, text = "第 ${index + 1} 句歌词")
            },
        )
        setContent(state = state, playerState = player)

        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithContentDescription("展开完整歌词").performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("lyrics-line-4", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag("lyrics-line-4", useUnmergedTree = true).performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, -360f),
                durationMillis = 400,
            )
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodes(
                SemanticsMatcher.keyIsDefined(LyricBlurRadiusKey),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().all { it.config[LyricBlurRadiusKey] < 0.05f }
        }

        compose.mainClock.autoAdvance = false
        try {
            compose.mainClock.advanceTimeBy(4_000)
            compose.onNodeWithTag("lyrics-list", useUnmergedTree = true).performTouchInput {
                swipe(
                    start = Offset(center.x, height * 0.45f),
                    end = Offset(center.x, height * 0.45f - 120f),
                    durationMillis = 240,
                )
            }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(7_800)
            val beforeTimeout = compose.onAllNodes(
                SemanticsMatcher.keyIsDefined(LyricBlurRadiusKey),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            assertTrue(
                "继续拖动歌词后应重新计算 8 秒空闲时间",
                beforeTimeout.isNotEmpty() &&
                    beforeTimeout.all { it.config[LyricBlurRadiusKey] < 0.05f },
            )
            compose.mainClock.advanceTimeBy(1_000)
        } finally {
            compose.mainClock.autoAdvance = true
        }

        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodes(
                SemanticsMatcher.keyIsDefined(LyricBlurRadiusKey),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().any { it.config[LyricBlurRadiusKey] > 0.1f }
        }
        val activeLine = compose.onNodeWithTag("lyrics-line-4", useUnmergedTree = true)
            .fetchSemanticsNode()
        assertTrue(
            "恢复自动跟随后，高亮歌词应回到屏幕焦点区域",
            activeLine.boundsInRoot.center.y in 700f..1_150f,
        )
        assertEquals(0f, activeLine.config[LyricBlurRadiusKey], 0.05f)
    }

    @Test
    fun lyricsTransitionKeepsTheActiveLineInPlaceWhileFadingOut() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            positionMs = 70_000,
            durationMs = 180_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = listOf(
                LyricLine(timeMs = 0, text = "开头歌词"),
                LyricLine(timeMs = 60_000, text = "当前歌词"),
                LyricLine(timeMs = 120_000, text = "末尾歌词"),
            ),
        )
        setContent(state = state, playerState = player)

        compose.onNodeWithText("测试曲目").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("player-lyrics-entry").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.mainClock.advanceTimeBy(340)
        val enteringLine = compose.onNodeWithTag("lyrics-line-1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        compose.mainClock.advanceTimeBy(280)
        val settledLine = compose.onNodeWithTag("lyrics-line-1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(
            "歌词应在渐显前完成居中，显现过程中不应再次跳动",
            enteringLine.top,
            settledLine.top,
            3f,
        )

        compose.onNodeWithTag("player-lyrics-header").performClick()
        val coverBeforeLyricExit = compose.onNodeWithTag("player-morph-cover")
            .fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(100)
        val coverDuringLyricExit = compose.onNodeWithTag("player-morph-cover")
            .fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(
            "歌词淡出阶段封面应保持静止",
            coverBeforeLyricExit.top,
            coverDuringLyricExit.top,
            2f,
        )
        org.junit.Assert.assertEquals(
            "歌词淡出阶段封面尺寸应保持不变",
            coverBeforeLyricExit.height,
            coverDuringLyricExit.height,
            2f,
        )
        compose.mainClock.advanceTimeBy(60)
        val exitingLine = compose.onNodeWithTag("lyrics-line-1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val maximumExitTranslation = with(compose.density) { 3.dp.toPx() }
        org.junit.Assert.assertTrue(
            "歌词退出时只应渐隐，不应发生位移，entering=${enteringLine.top}, settled=${settledLine.top}, exiting=${exitingLine.top}, maximum=$maximumExitTranslation",
            kotlin.math.abs(exitingLine.top - settledLine.top) <= maximumExitTranslation,
        )
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun roamingNowPlayingUsesTheRoamIndicatorInsteadOfTheQueueEntry() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
            isRoaming = true,
        )
        setContent(state = MusicUiState(loading = false), playerState = player)

        compose.onNodeWithText("测试曲目").performClick()

        compose.onNodeWithTag("player-roam-indicator").assertIsDisplayed()
        compose.onNodeWithContentDescription("漫游模式").assertIsDisplayed()
        compose.onNodeWithTag("player-queue-entry").assertDoesNotExist()
        compose.onNodeWithContentDescription("打开待播队列").assertDoesNotExist()
    }

    @Test
    fun lyricsPressFeedbackAppearsDuringShortTapAndClearsOnRelease() {
        val track = Track(TrackId("lyric-press"), "按压反馈验证")
        setContent(
            state = MusicUiState(loading = false, lyrics = listOf(
                LyricLine(0, "歌词按下效果"), LyricLine(10_000, "下一句歌词"),
            )),
            playerState = PlayerState(
                queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                currentIndex = 0, positionMs = 2_000, durationMs = 30_000,
            ),
        )
        compose.onNodeWithText("按压反馈验证").performClick()
        compose.onNodeWithContentDescription("展开完整歌词").performClick()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_200)
        val line = compose.onNodeWithTag("lyrics-line-0", useUnmergedTree = true)
        fun backgroundRed(): Int {
            val bitmap = line.captureToImage().asAndroidBitmap()
            return android.graphics.Color.red(bitmap.getPixel(10, bitmap.height / 2))
        }
        val resting = backgroundRed()
        line.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(80)
        val shortPress = backgroundRed()
        captureLyricsScreenshot("press-short")
        compose.mainClock.advanceTimeBy(300)
        captureLyricsScreenshot("press-held")
        line.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(300)
        val released = backgroundRed()
        assertTrue("短按 80ms 内应出现背景", shortPress > resting + 8)
        assertTrue("松手后按压背景应消失", kotlin.math.abs(released - resting) < 4)
        line.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(80)
        line.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(300)
        assertTrue("手势取消后按压背景应消失", kotlin.math.abs(backgroundRed() - resting) < 4)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun lyricFocusAndWrappingStayFixedWhenControlsHide() {
        val track = Track(TrackId("lyric-focus"), "歌词视觉验证")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            positionMs = 65_000,
            durationMs = 200_000,
        )
        val state = MusicUiState(loading = false, lyrics = listOf(
            LyricLine(0, "风从远处吹来"),
            LyricLine(30_000, "留住每一个温柔的瞬间"),
            LyricLine(60_000, "你所有难堪 我闭口不谈", translation = "All the words we leave unspoken"),
            LyricLine(90_000, "让这段旋律一直陪伴你走过漫长的夜晚"),
            LyricLine(120_000, "Hello，世界 👩🏽‍💻"),
            LyricLine(150_000, "风停了 歌还在继续"),
        ))
        setContent(state = state, playerState = player)
        compose.onNodeWithText("歌词视觉验证").performClick()
        compose.onNodeWithContentDescription("展开完整歌词").performClick()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_200)
        compose.waitForIdle()
        val visible = compose.onNodeWithTag("lyrics-line-2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag("player-lyrics-header", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("player-morph-overlay").fetchSemanticsNode().boundsInRoot
        assertTrue("焦点应位于顶部信息下方的阅读区上部", visible.top > header.bottom && visible.top < header.bottom + (root.bottom - header.bottom) * 0.25f)
        captureLyricsScreenshot("controls-visible")
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()
        val hidden = compose.onNodeWithTag("lyrics-line-2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(visible.top, hidden.top, 1f)
        assertEquals(visible.height, hidden.height, 1f)
        captureLyricsScreenshot("controls-hidden")
        compose.onNodeWithTag("lyrics-line-3", useUnmergedTree = true).performTouchInput {
            swipe(center, Offset(center.x, center.y - 100f), durationMillis = 500)
        }
        compose.mainClock.advanceTimeBy(400)
        captureLyricsScreenshot("manual-browsing")
        compose.mainClock.advanceTimeBy(4_000)
        captureLyricsScreenshot("manual-controls-hidden")
        assertTrue(compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(LyricBlurRadiusKey), useUnmergedTree = true,
        ).fetchSemanticsNodes().all { it.config[LyricBlurRadiusKey] < 0.05f })
        compose.mainClock.autoAdvance = true
    }

    private fun captureLyricsScreenshot(name: String) {
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { java.io.File(it).apply { mkdirs() } }
            ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("lyrics-screenshots")!!
        java.io.File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun lyricsDragDoesNotRestartControlsEntranceAnimation() {
        val track = Track(TrackId("lyrics-continuous-drag"), "持续拖动验证")
        setContent(
            state = MusicUiState(loading = false, lyrics = (0..30).map { LyricLine(text = "歌词第 $it 行") }),
            playerState = PlayerState(
                queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                currentIndex = 0, durationMs = 180_000,
            ),
        )
        compose.onNodeWithText("持续拖动验证").performClick()
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_200)
        val controls = compose.onNodeWithTag("player-bottom-controls").fetchSemanticsNode()
        val restingTop = controls.boundsInRoot.top
        val listBounds = compose.onNodeWithTag("lyrics-list").fetchSemanticsNode().boundsInWindow
        var decor: android.view.View? = null
        compose.runOnUiThread {
            decor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).single().window.decorView
        }
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithContentDescription("播放或暂停").assertDoesNotExist()
        val start = Offset(listBounds.center.x, listBounds.top + listBounds.height * 0.4f)
        val downTime = android.os.SystemClock.uptimeMillis()
        var eventTime = downTime
        var position = start
        fun dispatch(action: Int) {
            compose.runOnUiThread {
                val event = android.view.MotionEvent.obtain(downTime, eventTime, action, position.x, position.y, 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                decor!!.dispatchTouchEvent(event)
                event.recycle()
            }
        }
        // Espresso waits for list scrolling to be idle. Dispatch and inspect existing coordinates
        // directly on the UI thread so the assertions can run while the finger is still down.
        dispatch(android.view.MotionEvent.ACTION_DOWN)
        repeat(24) { index ->
            eventTime += 16
            val step = (index % 12).let { if (it < 6) it + 1 else 11 - it }
            position = start + Offset(0f, -step * 12f)
            dispatch(android.view.MotionEvent.ACTION_MOVE)
            compose.mainClock.advanceTimeBy(16)
        }
        compose.runOnUiThread {
            assertEquals("持续滑动时入场动画仍应完成", restingTop, controls.boundsInRoot.top, 2f)
        }
        compose.mainClock.advanceTimeBy(5_000)
        compose.runOnUiThread {
            assertEquals("手指停住时仍应保持显示", restingTop, controls.boundsInRoot.top, 2f)
        }
        eventTime += 5_000
        dispatch(android.view.MotionEvent.ACTION_UP)
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithContentDescription("播放或暂停").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun lyricsControlsStayVisibleWhileSeeking() {
        verifyLyricsSliderKeepsControlsVisible("player-playback-progress")
    }

    @Test
    fun lyricsControlsStayVisibleWhileChangingVolume() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val originalVolume = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        try {
            verifyLyricsSliderKeepsControlsVisible("player-volume-slider")
        } finally {
            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, originalVolume, 0)
        }
    }

    private fun verifyLyricsSliderKeepsControlsVisible(tag: String) {
        val track = Track(TrackId("slider-hold"), "拖拽控制验证")
        setContent(
            state = MusicUiState(loading = false, lyrics = listOf(LyricLine(text = "第一句"))),
            playerState = PlayerState(
                queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                currentIndex = 0, durationMs = 180_000,
            ),
        )
        compose.onNodeWithText("拖拽控制验证").performClick()
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_200)
        val slider = compose.onNodeWithTag(tag)
        slider.performTouchInput {
            down(center)
            moveTo(Offset(width * 0.7f, center.y))
        }
        // A stationary finger after dragging must also keep the controls alive.
        compose.mainClock.advanceTimeBy(5_000)
        compose.onNodeWithContentDescription("播放或暂停").assertIsDisplayed()
        slider.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2_800)
        compose.onNodeWithContentDescription("播放或暂停").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithContentDescription("播放或暂停").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun lyricsControlsHideAfterThreeSecondsAndReturnOnInteraction() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = listOf(LyricLine(text = "第一句"), LyricLine(text = "第二句")),
        )
        setContent(state = state, playerState = player)
        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithTag("player-lyrics-entry").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.mainClock.advanceTimeBy(420)
        compose.onNodeWithContentDescription("播放或暂停").assertIsDisplayed()

        compose.mainClock.advanceTimeBy(2_900)
        compose.onNodeWithContentDescription("播放或暂停").assertIsDisplayed()

        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("播放或暂停").assertDoesNotExist()

        compose.onNodeWithText("第一句").performClick()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithContentDescription("播放或暂停").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun trackMoreMenuDispatchesPlayNextAndUsesACommandSheet() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        var playedNext: Track? = null
        setContent(
            state = MusicUiState(loading = false, tracks = listOf(track)),
            onPlayNext = { playedNext = it },
        )

        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("下一首播放").assertIsDisplayed().performClick()

        org.junit.Assert.assertEquals(track.id, playedNext?.id)
    }

    @Test
    fun playerAlbumNavigationCollapsesAndQualityIsCentered() {
        val album = Album(AlbumId("album"), "测试专辑")
        val track = Track(TrackId("track"), "测试曲目", album = album,
            audioSpec = AudioSpec(format = "flac", codec = "flac", sampleRate = 96000, bitDepth = 24))
        setContent(playerState = PlayerState(queue = listOf(PlayableTrack(track, "https://music.invalid/stream")), currentIndex = 0))
        compose.onNodeWithTag("dynamic-mini-player").performClick()
        val quality = compose.onNodeWithTag("player-quality").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val progress = compose.onNodeWithTag("player-playback-progress").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(quality.center.x - progress.center.x) < 2f)
        assertTrue(quality.top >= progress.bottom)
        captureQueueScreenshot("now-playing-quality")
        compose.onNodeWithTag("player-more-action").performClick()
        compose.onNodeWithText("查看专辑").performClick()
        compose.onNodeWithTag("player-morph-overlay").assertDoesNotExist()
        compose.onNodeWithTag("dynamic-mini-player").assertIsDisplayed()
        compose.onNodeWithText("测试专辑").assertIsDisplayed()
    }

    @Test
    fun songInformationShowsLongPathAndOpensEditor() {
        val path = "/vol2/music/一个很长的专辑文件夹/原始音乐文件/测试曲目 - 测试歌手.flac"
        val track = Track(TrackId("track"), "测试曲目", discNo = 1, createdAt = 1788520908)
        val metadata = TrackMetadata(track, AudioSpec(format = "flac", path = path, bitrate = 847806))
        setContent(state = MusicUiState(loading = false, tracks = listOf(track), detailMetadata = metadata),
            onSaveTrackMetadata = { _, _ -> metadata })
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("歌曲信息").performClick()
        compose.onNodeWithText(path).performScrollTo().assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.hasScrollAction()).performTouchInput {
            swipe(center, center - Offset(0f, 400f), 500)
        }
        compose.onNodeWithText("文件位置").assertIsDisplayed()
        captureQueueScreenshot("track-file-information")
        compose.onNodeWithText("编辑").assertIsDisplayed().performClick()
        compose.onNodeWithText("编辑歌曲信息").assertIsDisplayed()
        // Dialog window entrance is driven by Android rather than the Compose test clock.
        android.os.SystemClock.sleep(500)
        captureQueueScreenshot("track-metadata-editor")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("编辑歌曲信息").assertDoesNotExist()
    }

    @Test
    fun songInformationAppBarRemainsVisibleWhenScrolling() {
        val track = Track(TrackId("track"), "固定标题测试", audioSpec = AudioSpec(path = "/music/song.flac"))
        setContent(state = MusicUiState(loading = false, tracks = listOf(track), detailMetadata = TrackMetadata(track)),
            onSaveTrackMetadata = { _, _ -> TrackMetadata(track) })
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("歌曲信息").performClick()
        val before = compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot
        compose.onNode(androidx.compose.ui.test.hasScrollAction()).performTouchInput {
            swipe(center, center - Offset(0f, 700f), 500)
        }
        val after = compose.onNodeWithContentDescription("返回").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
        compose.onNodeWithText("歌曲信息").assertIsDisplayed()
        compose.onNodeWithText("编辑").assertIsDisplayed()
    }

    @Test
    fun albumDetailUsesFullMetadataAndKeepsItsAppBarFixed() {
        val partial = Album(AlbumId("album"), "详情接口专辑")
        val artist = Artist(ArtistId("artist"), "详情接口歌手")
        val complete = partial.copy(artists = listOf(artist), trackCount = 6, releaseDate = "2022-09-20")
        val tracks = (1..6).map { Track(TrackId("t$it"), "曲目 $it", album = partial) }
        setContent(state = MusicUiState(loading = false, tracks = listOf(tracks.first()),
            detailTracks = tracks, detailAlbum = complete))
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("查看专辑").performClick()
        compose.onNodeWithText("2022 年").assertIsDisplayed()
        compose.onNodeWithText("详情接口歌手").assertIsDisplayed()
        val before = compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("library-detail-list").performScrollToNode(hasText("曲目 6"))
        val after = compose.onNodeWithContentDescription("返回").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
        captureQueueScreenshot("album-fixed-app-bar")
    }

    @Test
    fun playerInformationNavigationCollapsesToMiniPlayer() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        setContent(playerState = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")), currentIndex = 0,
        ))
        compose.onNodeWithTag("dynamic-mini-player").performClick()
        compose.onNodeWithTag("player-more-action").performClick()
        compose.onNodeWithText("歌曲信息").performClick()
        compose.onNodeWithTag("player-morph-overlay").assertDoesNotExist()
        compose.onNodeWithTag("dynamic-mini-player").assertIsDisplayed()
        compose.onNodeWithText("音频").assertIsDisplayed()
    }

    @Test
    fun songInformationOpensAsAFullPageWithAudioMetadata() {
        val artist = Artist(ArtistId("artist-placeholder"), "测试歌手")
        val track = Track(TrackId("track-placeholder"), "测试曲目", artists = listOf(artist))
        val metadata = TrackMetadata(
            track = track,
            audioSpec = AudioSpec(format = "flac", codec = "FLAC", size = 12L * 1024L * 1024L, sampleRate = 48_000, bitDepth = 24, channel = 2),
        )
        setContent(state = MusicUiState(loading = false, tracks = listOf(track), detailMetadata = metadata))

        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("歌曲信息").performClick()

        compose.onNodeWithText("音频").assertIsDisplayed()
        compose.onNodeWithText("48.0 kHz").assertIsDisplayed()
        compose.onNodeWithText("24 bit").assertIsDisplayed()
        compose.onNodeWithText("首页").assertIsDisplayed()
        captureQueueScreenshot("track-audio-information")
    }

    @Test
    fun searchShowsCrossCategorySuggestionsBeforePagedResults() {
        val track = Track(TrackId("track-placeholder"), "建议歌曲")
        val album = Album(AlbumId("album-placeholder"), "建议专辑")
        setContent(
            state = MusicUiState(
                loading = false,
                searchQuery = "建议",
                searchSuggestions = SearchSuggestions(tracks = listOf(track), albums = listOf(album)),
            ),
        )

        compose.onNodeWithTag("dynamic-search").performClick()

        compose.onNodeWithText("快速匹配").assertIsDisplayed()
        compose.onNodeWithText("建议歌曲").assertIsDisplayed()
        compose.onNodeWithText("建议专辑").assertIsDisplayed()
    }

    @Test
    fun librarySortMenuDispatchesTheSelectedServerSort() {
        var selected: TrackSort? = null
        setContent(onTrackSort = { selected = it })

        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("全部歌曲").performClick()
        compose.onNodeWithContentDescription("排序").performClick()
        compose.onNodeWithText("歌曲名 Z–A").performClick()

        compose.runOnIdle { org.junit.Assert.assertEquals(TrackSort.TitleDescending, selected) }
    }

    @Test
    fun libraryShowsServerReportedTotalInsteadOfLoadedItemCount() {
        setContent(state = MusicUiState(loading = false, trackTotal = 128))

        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("全部歌曲").performClick()

        compose.onNodeWithText("共 128 首歌曲").assertIsDisplayed()
        compose.onNodeWithText("0 首已加载歌曲").assertDoesNotExist()
    }

    @Test
    fun playlistLibraryOpensAFullPageEditorInsteadOfBeingReadOnly() {
        val playlist = Playlist(PlaylistId("playlist-placeholder"), "测试歌单", trackCount = 3)
        setContent(state = MusicUiState(loading = false, playlists = listOf(playlist)))

        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("歌单").performClick()
        compose.onNodeWithText("只读").assertDoesNotExist()
        compose.onNodeWithContentDescription("新建歌单").performClick()

        compose.onNodeWithText("新建歌单").assertIsDisplayed()
        compose.onNodeWithText("歌单名称").assertIsDisplayed()
        compose.onNodeWithText("保存歌单").assertIsDisplayed()
        compose.onNodeWithText("首页").assertIsDisplayed()
    }

    @Test
    fun trackCanBeAddedToAnExistingPlaylistFromItsCommandSheet() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val playlist = Playlist(PlaylistId("playlist-placeholder"), "通勤歌单", trackCount = 2)
        var target: Pair<PlaylistId, TrackId>? = null
        setContent(
            state = MusicUiState(loading = false, tracks = listOf(track), playlists = listOf(playlist)),
            onAddTrackToPlaylist = { playlistId, trackId -> target = playlistId to trackId },
        )

        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("添加到歌单").performClick()
        compose.onNodeWithContentDescription("添加到歌单：通勤歌单").performClick()

        org.junit.Assert.assertEquals(playlist.id to track.id, target)
    }

    @Test
    fun creatingAPlaylistFromTrackActionsKeepsThePendingTrack() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        var createdWith: Triple<String, String?, TrackId?>? = null
        setContent(
            state = MusicUiState(loading = false, tracks = listOf(track)),
            onCreatePlaylist = { name, coverId, initialTrackId -> createdWith = Triple(name, coverId, initialTrackId) },
        )

        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("添加到歌单").performClick()
        compose.onNodeWithText("新建歌单").performClick()
        compose.onNodeWithText("歌单名称").performTextInput("旅途")
        compose.onNodeWithText("保存歌单").performClick()

        org.junit.Assert.assertEquals(Triple("旅途", "playlist_default_1", track.id), createdWith)
    }

    @Test
    fun playlistDetailSupportsBatchTrackRemoval() {
        val first = Track(TrackId("track-one"), "第一首")
        val second = Track(TrackId("track-two"), "第二首")
        val playlist = Playlist(PlaylistId("playlist-placeholder"), "通勤歌单", trackCount = 2)
        var removed: Pair<PlaylistId, List<TrackId>>? = null
        setContent(
            state = MusicUiState(
                loading = false,
                playlists = listOf(playlist),
                detailTracks = listOf(first, second),
            ),
            onRemoveTracksFromPlaylist = { playlistId, trackIds -> removed = playlistId to trackIds },
        )

        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("歌单").performClick()
        compose.onNodeWithText("通勤歌单").performClick()
        compose.onNodeWithText("多选").performClick()
        compose.onNodeWithText("第一首").performClick()
        compose.onNodeWithText("移除 1 首").performClick()

        org.junit.Assert.assertEquals(playlist.id to listOf(first.id), removed)
    }

    @Test
    fun detailTabsPreserveIndependentStacksAndBackReturnsToTrackInformation() {
        val album = Album(AlbumId("nested-album"), "导航测试专辑")
        val track = Track(TrackId("nested-track"), "导航测试歌曲", album = album)
        setContent(state = MusicUiState(loading = false, tracks = listOf(track)))
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("歌曲信息").performClick()
        compose.onNode(hasText("导航测试专辑") and androidx.compose.ui.test.hasClickAction())
            .performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("album-play").assertIsDisplayed()
        if (compose.onAllNodesWithTag("dynamic-primary-tab").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("dynamic-primary-tab").performClick()
        }
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("首页").performClick()
        compose.onNodeWithTag("album-play").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("歌曲信息").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("更多操作").assertIsDisplayed()
    }

    @Test
    fun playlistEditorDraftSurvivesTabSwitch() {
        setContent()
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("歌单").performClick()
        compose.onNodeWithContentDescription("新建歌单").performClick()
        compose.onNodeWithText("歌单名称").performTextInput("保留草稿")
        compose.onNodeWithText("首页").performClick()
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("保留草稿").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("新建歌单").assertIsDisplayed()
    }

    @Test
    fun detailMiniPlayerReturnsToTheSameAlbum() {
        val album = Album(AlbumId("player-album"), "播放返回专辑")
        val track = Track(TrackId("player-track"), "播放返回歌曲")
        val player = PlayerState(queue = listOf(PlayableTrack(track, "https://music.invalid/stream")), currentIndex = 0)
        setContent(state = MusicUiState(loading = false), albums = PagingData.from(listOf(album)), playerState = player)
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("全部专辑").performClick()
        compose.onNodeWithText("播放返回专辑").performClick()
        compose.onNodeWithTag("dynamic-mini-player").assertIsDisplayed().performClick()
        compose.onNodeWithTag("player-morph-cover").assertIsDisplayed()
        compose.onRoot().performTouchInput { swipe(Offset(center.x, center.y / 2), Offset(center.x, height * .9f), 700) }
        compose.waitForIdle()
        compose.onNodeWithText("播放返回专辑").assertIsDisplayed()
        compose.onNodeWithTag("dynamic-mini-player").assertIsDisplayed()
    }

    @Test
    fun editorDraftAndSelectedTabSurviveConfigurationRestore() {
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        setContent(restoration = restoration)
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("歌单").performClick()
        compose.onNodeWithContentDescription("新建歌单").performClick()
        compose.onNodeWithText("歌单名称").performTextInput("旋转后草稿")
        compose.onNodeWithText("首页").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("旋转后草稿").assertIsDisplayed()
    }

    @Test
    fun detailScrollSurvivesReloadAndRejectsAnUnrelatedResponse() {
        val album = Album(AlbumId("scroll-album"), "长列表专辑", trackCount = 40)
        val tracks = (1..40).map { Track(TrackId("scroll-$it"), "列表歌曲 $it") }
        val ready = MusicUiState(loading = false, detailKey = DetailRequestKey("album", album.id.value), detailTracks = tracks)
        val model = mutableStateOf(ready)
        setContent(stateProvider = { model.value }, albums = PagingData.from(listOf(album)))
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("全部专辑").performClick()
        compose.onNodeWithText("长列表专辑").performClick()
        compose.onNodeWithTag("library-detail-list").performScrollToNode(hasText("列表歌曲 30"))
        val before = compose.onNodeWithText("列表歌曲 30").fetchSemanticsNode().boundsInRoot.top
        if (compose.onAllNodesWithTag("dynamic-primary-tab").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("dynamic-primary-tab").performClick()
        }
        compose.onNodeWithText("首页").performClick()
        compose.runOnIdle { model.value = ready.copy(detailKey = DetailRequestKey("playlist", "unrelated"), detailTracks = listOf(Track(TrackId("wrong"), "错误歌曲"))) }
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("错误歌曲").assertDoesNotExist()
        compose.onNodeWithContentDescription("正在加载曲目").assertIsDisplayed()
        compose.runOnIdle { model.value = ready }
        val after = compose.onNodeWithText("列表歌曲 30").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 3f)
    }

    @Test
    fun liquidGlassSettingsReturnToSettingsWithToolbarAndSystemBack() {
        setContent()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("Liquid Glass").performClick()
        compose.onNodeWithTag("liquid-glass-page").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("settings-page").assertIsDisplayed()
        compose.onNodeWithText("Liquid Glass").performClick()
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("settings-page").assertIsDisplayed()
        compose.onNodeWithText("自动缓存歌曲").assertIsDisplayed()
        compose.onNodeWithTag("dynamic-bottom-bar").assertIsDisplayed()
    }

    @Test
    fun systemBackPopsMoreSubpageBeforeLeavingTheApp() {
        setContent()
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("歌单").performClick()
        compose.onNodeWithContentDescription("新建歌单").assertIsDisplayed()
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("最近播放").assertIsDisplayed()
        compose.onNodeWithText("全部歌曲").assertIsDisplayed()
        compose.onNodeWithTag("dynamic-bottom-bar").assertIsDisplayed()
    }

    @Test
    fun miniPlayerDeformsDuringDragAndSpringsBackWithoutOpening() {
        assertLiquidMiniPlayerDrag(collapsed = false)
    }

    @Test
    fun collapsedMiniPlayerKeepsTheSameLiquidDragInteraction() {
        assertLiquidMiniPlayerDrag(collapsed = true)
    }

    private fun assertLiquidMiniPlayerDrag(collapsed: Boolean) {
        val track = Track(TrackId("liquid-track"), "液态播放条")
        val player = PlayerState(queue = listOf(PlayableTrack(track, "https://music.invalid/stream")), currentIndex = 0)
        val tracks = (1..12).map { Track(TrackId("home-liquid-$it"), "首页曲目 $it") }
        setContent(state = MusicUiState(loading = false, tracks = tracks), playerState = player)
        if (collapsed) {
            compose.onRoot().performTouchInput {
                swipe(Offset(center.x, center.y * 1.3f), Offset(center.x, center.y * .5f), 500)
            }
            compose.onNodeWithTag("dynamic-primary-tab").assertIsDisplayed()
        }
        val miniPlayer = compose.onNodeWithTag("dynamic-mini-player")
        val cover = compose.onNodeWithTag("dynamic-cover", useUnmergedTree = true)
        val resting = cover.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        try {
            miniPlayer.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(240)
            val pressed = cover.fetchSemanticsNode().boundsInRoot
            assertTrue("按住播放条时封面应随胶囊形变", pressed.height > resting.height + 2f)
            miniPlayer.performTouchInput { moveBy(Offset(140f, -10f), delayMillis = 160) }
            compose.mainClock.advanceTimeBy(100)
            val dragged = cover.fetchSemanticsNode().boundsInRoot
            assertTrue("胶囊应跟随拖拽产生阻尼位移", dragged.center.x > pressed.center.x + 1f)
            miniPlayer.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1_500)
            val released = cover.fetchSemanticsNode().boundsInRoot
            assertEquals(resting.height, released.height, 1f)
            assertEquals(resting.center.x, released.center.x, 1f)
            compose.onNodeWithTag("player-morph-overlay").assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
        miniPlayer.performClick()
        compose.onNodeWithTag("player-morph-overlay").assertIsDisplayed()
    }

    @Test
    fun miniPlayerButtonsDoNotOpenThePlayerAndRemainClickableAfterDrag() {
        val track = Track(TrackId("liquid-controls"), "播放条控制")
        var toggles = 0
        val player = PlayerState(queue = listOf(PlayableTrack(track, "https://music.invalid/stream")), currentIndex = 0)
        setContent(playerState = player, onTogglePlayback = { toggles++ })
        compose.onNodeWithTag("dynamic-mini-player").performTouchInput {
            swipe(center, center + Offset(120f, 0f), 400)
        }
        compose.onNodeWithContentDescription("播放或暂停").performClick()
        compose.runOnIdle { assertEquals(1, toggles) }
        compose.onNodeWithTag("player-morph-overlay").assertDoesNotExist()
    }

    private fun captureQueueScreenshot(name: String) {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("queue-screenshots")!!
        java.io.File(directory, "$name.png").outputStream().use {
            val bitmap = if (name == "track-metadata-editor") InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                else compose.onRoot().captureToImage().asAndroidBitmap()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun playlistSelectionSurvivesTabSwitchAndConfigurationRestore() {
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        val playlist = Playlist(PlaylistId("retained-selection"), "保留选择歌单", trackCount = 1)
        val track = Track(TrackId("selected-track"), "保留选中歌曲")
        setContent(
            state = MusicUiState(loading = false, playlists = listOf(playlist), detailTracks = listOf(track)),
            restoration = restoration,
        )
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("歌单").performClick()
        compose.onNodeWithText("保留选择歌单").performClick()
        compose.onNodeWithText("多选").performScrollTo().performClick()
        compose.onNodeWithText("保留选中歌曲").performScrollTo().performClick()
        if (compose.onAllNodesWithTag("dynamic-primary-tab").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("dynamic-primary-tab").performClick()
        }
        compose.onNodeWithText("首页").performClick()
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("移除 1 首").performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("移除 1 首").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun albumGridScrollSurvivesSwitchingTabs() {
        val albums = (1..60).map { Album(AlbumId("grid-$it"), "专辑编号 $it") }
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        setContent(restoration = restoration, albums = PagingData.from(albums))
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("全部专辑").performClick()
        assertGridScrollSurvivesTabs("album-grid", "专辑编号 40")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("专辑编号 40").assertIsDisplayed()
    }

    @Test
    fun playlistGridScrollSurvivesSwitchingTabs() {
        val playlists = (1..60).map { Playlist(PlaylistId("grid-$it"), "歌单编号 $it") }
        setContent(state = MusicUiState(loading = false, playlists = playlists))
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("歌单").performClick()
        assertGridScrollSurvivesTabs("playlist-grid", "歌单编号 40")
    }

    private fun assertGridScrollSurvivesTabs(grid: String, item: String) {
        compose.onNodeWithTag(grid).performScrollToNode(hasText(item))
        val before = compose.onNodeWithText(item).fetchSemanticsNode().boundsInRoot.top
        if (compose.onAllNodesWithTag("dynamic-primary-tab").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("dynamic-primary-tab").performClick()
        }
        compose.onNodeWithText("首页").performClick()
        compose.onNodeWithContentDescription("音乐库").performClick()
        val after = compose.onNodeWithText(item).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 3f)
    }

    @Test
    fun cachedAlbumRetainsItsScrollWhileAnotherDetailIsLoading() {
        val album = Album(AlbumId("cached-scroll"), "缓存专辑", trackCount = 40)
        val tracks = (1..40).map { Track(TrackId("cached-$it"), "缓存曲目 $it") }
        val ready = MusicUiState(
            loading = false, detailKey = DetailRequestKey("album", album.id.value), detailTracks = tracks,
        ).cacheCurrentDetail()
        val model = mutableStateOf(ready)
        setContent(stateProvider = { model.value }, albums = PagingData.from(listOf(album)))
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("全部专辑").performClick()
        compose.onNodeWithText("缓存专辑").performClick()
        compose.onNodeWithTag("library-detail-list").performScrollToNode(hasText("缓存曲目 30"))
        val before = compose.onNodeWithText("缓存曲目 30").fetchSemanticsNode().boundsInRoot.top
        if (compose.onAllNodesWithTag("dynamic-primary-tab").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("dynamic-primary-tab").performClick()
        }
        compose.onNodeWithText("首页").performClick()
        compose.runOnIdle { model.value = ready.forDetail(DetailRequestKey("playlist", "other")) }
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("正在加载详情…").assertDoesNotExist()
        val after = compose.onNodeWithText("缓存曲目 30").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 3f)
    }

    @Test
    fun playerPredictiveBackCancelsThenReturnsToTheUnderlyingPage() {
        val input = androidx.navigationevent.DirectNavigationEventInput()
        val track = Track(TrackId("predictive-player"), "可预测返回歌曲")
        setContent(
            playerState = PlayerState(queue = listOf(PlayableTrack(track, "https://music.invalid/stream")), currentIndex = 0),
            backInput = input,
        )
        compose.onNodeWithTag("dynamic-mini-player").performClick()
        val cover = compose.onNodeWithTag("player-morph-cover")
        val expanded = cover.fetchSemanticsNode().boundsInRoot.width
        fun event(progress: Float) = androidx.navigationevent.NavigationEvent(progress = progress, swipeEdge = androidx.navigationevent.NavigationEvent.EDGE_LEFT)
        compose.runOnIdle { input.backStarted(event(0f)) }
        compose.runOnIdle { input.backProgressed(event(.5f)) }
        compose.waitForIdle()
        assertTrue(cover.fetchSemanticsNode().boundsInRoot.width < expanded)
        compose.runOnIdle { input.backCancelled() }
        compose.waitForIdle()
        assertEquals(expanded, cover.fetchSemanticsNode().boundsInRoot.width, 2f)
        compose.runOnIdle { input.backStarted(event(0f)) }
        compose.runOnIdle { input.backProgressed(event(.7f)) }
        compose.runOnIdle { input.backCompleted() }
        compose.waitForIdle()
        compose.onNodeWithTag("player-morph-overlay").assertDoesNotExist()
        compose.onNodeWithTag("dynamic-mini-player").assertIsDisplayed()
    }

    @Test
    fun bufferingStateShowsProgressFeedbackOnThePlayerPage() {
        val track = Track(TrackId("buffering"), "缓冲状态测试")
        setContent(
            playerState = PlayerState(
                queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                currentIndex = 0,
                playbackStatus = PlaybackStatus.Buffering,
            ),
        )

        compose.onNodeWithTag("dynamic-mini-player").performClick()
        compose.onNodeWithContentDescription("播放或暂停").assertIsDisplayed()
    }

    @Test
    fun homeCollectionLinksReturnToHomeWithoutReplacingLibraryStack() {
        setContent()
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("歌单").performClick()
        compose.onNodeWithText("首页").performClick()
        compose.onNodeWithContentDescription("收藏 快捷入口").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("随机漫游").assertIsDisplayed()
        compose.onNodeWithContentDescription("查看专辑").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("查看最近添加").performClick()
        compose.onNodeWithText("全部歌曲").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithContentDescription("新建歌单").assertIsDisplayed()
    }

    @Test
    fun collectionSortIsIndependentAcrossTabsAndRestores() {
        var requestedSort: TrackSort? = null
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        setContent(restoration = restoration, onTrackSort = { requestedSort = it })
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("全部歌曲").performClick()
        compose.onNodeWithContentDescription("排序").performClick()
        compose.onNodeWithText("歌曲名 Z–A").performClick()
        compose.runOnIdle { assertEquals(TrackSort.TitleDescending, requestedSort) }
        compose.onNodeWithText("首页").performClick()
        compose.onNodeWithContentDescription("查看最近添加").performClick()
        compose.runOnIdle { assertEquals(TrackSort.RecentlyAdded, requestedSort) }
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.runOnIdle { assertEquals(TrackSort.TitleDescending, requestedSort) }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(TrackSort.TitleDescending, requestedSort) }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("library-menu").assertIsDisplayed()
    }

    @Test
    fun trackScrollSurvivesHomeCollectionVisitAndConfigurationRestore() {
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        setContent(restoration = restoration, tracks = PagingData.from((1..80).map {
            Track(TrackId("scroll-track-$it"), "歌曲编号 $it")
        }))
        compose.onNodeWithContentDescription("音乐库").performClick()
        compose.onNodeWithText("全部歌曲").performClick()
        compose.onNodeWithText("歌曲编号 40").assertDoesNotExist()
        compose.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToNode(hasText("歌曲编号 40"))
        val before = compose.onNodeWithText("歌曲编号 40").fetchSemanticsNode().boundsInRoot.top
        if (compose.onAllNodesWithTag("dynamic-primary-tab").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("dynamic-primary-tab").performClick()
        }
        compose.onNodeWithText("首页").performClick()
        compose.onNodeWithContentDescription("查看最近添加").performClick()
        compose.onNodeWithText("歌曲编号 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("音乐库").performClick()
        val restored = compose.onNodeWithText("歌曲编号 40").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, restored, 3f)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("歌曲编号 40").assertIsDisplayed()
    }

    @Test
    fun profileIsARootPageAndSearchRemainsIndependent() {
        setContent()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithTag("settings-page").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").assertDoesNotExist()
        compose.onNodeWithText("Liquid Glass").performClick()
        compose.onNodeWithTag("dynamic-search").performClick()
        compose.onNodeWithText("搜索歌曲、歌手、专辑、歌单").assertIsDisplayed()
        compose.onNodeWithTag("dynamic-search").assertIsSelected()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithTag("liquid-glass-page").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("settings-page").assertIsDisplayed()
    }

    @Test
    fun searchDetailReturnsToItsQueryAfterSwitchingTabs() {
        val album = Album(AlbumId("search-nav"), "搜索导航专辑")
        setContent(state = MusicUiState(
            loading = false, searchQuery = "导航",
            searchSuggestions = SearchSuggestions(albums = listOf(album)),
        ))
        compose.onNodeWithTag("dynamic-search").performClick()
        compose.onNodeWithText("搜索导航专辑").performClick()
        compose.onNodeWithTag("album-play").assertIsDisplayed()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithTag("dynamic-search").performClick()
        compose.onNodeWithTag("album-play").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("导航").assertIsDisplayed()
        compose.onNodeWithText("快速匹配").assertIsDisplayed()
    }

    @Test
    fun mediumNavigationShowsFourDestinations() {
        assertWideNavigation(700)
    }

    @Test
    fun expandedNavigationShowsFourDestinations() {
        assertWideNavigation(1000)
    }

    private fun assertWideNavigation(width: Int) {
        setContent(windowSize = { androidx.compose.ui.unit.DpSize(width.dp, 844.dp) })
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithTag("settings-page").assertIsDisplayed()
        compose.onNodeWithText("音乐库").performClick()
        compose.onNodeWithTag("library-menu").assertIsDisplayed()
        compose.onNodeWithText("全部歌曲").performClick()
        compose.onNodeWithText("首页").assertIsDisplayed()
        compose.onNodeWithText("我的").assertIsDisplayed()
        compose.onNodeWithText("搜索").performClick()
        compose.onNodeWithText("搜索歌曲、歌手、专辑、歌单").assertIsDisplayed()
    }

    private fun setContent(
        state: MusicUiState = MusicUiState(loading = false),
        restoration: androidx.compose.ui.test.junit4.StateRestorationTester? = null,
        backInput: androidx.navigationevent.DirectNavigationEventInput? = null,
        stateProvider: (() -> MusicUiState)? = null,
        playerState: PlayerState = PlayerState(),
        playerStateProvider: (() -> PlayerState)? = null,
        albums: PagingData<Album> = PagingData.empty(),
        tracks: PagingData<Track> = PagingData.empty(),
        onToggleShuffle: () -> Unit = {},
        onCycleRepeatMode: () -> Unit = {},
        onTogglePlayback: () -> Unit = {},
        onNext: () -> Unit = {},
        onPrevious: () -> Unit = {},
        onSeek: (Long) -> Unit = {},
        onToggleFavorite: (Track) -> Unit = {},
        onPlayNext: (Track) -> Unit = {},
        onTrackSort: (TrackSort) -> Unit = {},
        onRoam: () -> Unit = {},
        onSkipToQueueItem: (Int) -> Unit = {},
        onSkipToHistoryItem: (Int) -> Unit = {},
        onClearPlaybackHistory: () -> Unit = {},
        onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
        onRemoveFromQueue: (Int) -> Unit = {},
        onCreatePlaylist: (String, String?, TrackId?) -> Unit = { _, _, _ -> },
        onAddTrackToPlaylist: (PlaylistId, TrackId) -> Unit = { _, _ -> },
        onRemoveTracksFromPlaylist: (PlaylistId, List<TrackId>) -> Unit = { _, _ -> },
        onSaveTrackMetadata:
            (suspend (
                Track,
                com.seasonyuu.fnmusic.core.model.TrackMetadataEdit) -> TrackMetadata)? =
            null,
        openPlayerRequested: () -> Boolean = { false },
        onPlayerOpenRequestConsumed: () -> Unit = {},
        windowSize: (() -> androidx.compose.ui.unit.DpSize)? = {
            androidx.compose.ui.unit.DpSize(390.dp, 844.dp)
        },
        testFontScale: () -> Float = { 1f },
    ) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            val detailKey =
                androidx.compose.runtime.remember { mutableStateOf<DetailRequestKey?>(null) }
            val pagingScope = androidx.compose.runtime.rememberCoroutineScope()
            val trackFlow = androidx.compose.runtime.remember(tracks) { flowOf(tracks).cachedIn(pagingScope) }
            val albumFlow = androidx.compose.runtime.remember(albums) { flowOf(albums).cachedIn(pagingScope) }
            MusicTestWindow(windowSize?.invoke(), testFontScale()) {
                FnMusicTheme {
                    MusicShell(
                        playerWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
                        immersivePlayerInsets = androidx.compose.foundation.layout.WindowInsets(0),
                        managePlayerSystemBars = false,
                        state = stateProvider?.invoke() ?: state.copy(detailKey = detailKey.value),
                        playerState = playerStateProvider?.invoke() ?: playerState,
                        pagedTracks = { sort -> onTrackSort(sort); trackFlow },
                        pagedAlbums = { albumFlow },
                        pagedArtists = flowOf(PagingData.empty()),
                        pagedFavorites = flowOf(PagingData.empty()),
                        pagedSearch = flowOf(PagingData.empty<SearchItem>()),
                        coverUrl = { _, _ -> null },
                        onRefresh = {},
                        onSearch = {},
                        onSearchType = { _: SearchType -> },
                        onRoam = onRoam,
                        onPlayAllTracks = {},
                        onPlayAllFavorites = {},
                        onLoadAlbum = { detailKey.value = DetailRequestKey("album", it.value) },
                        onLoadArtist = { detailKey.value = DetailRequestKey("artist", it.value) },
                        onLoadPlaylist = {
                            detailKey.value = DetailRequestKey("playlist", it.value)
                        },
                        onCreatePlaylist = onCreatePlaylist,
                        onUpdatePlaylist = { _, _, _ -> },
                        onDeletePlaylist = {},
                        onAddTrackToPlaylist = onAddTrackToPlaylist,
                        onRemoveTracksFromPlaylist = onRemoveTracksFromPlaylist,
                        onPurgeInvalidPlaylistTracks = {},
                        onLoadTrackMetadata = {
                            detailKey.value = DetailRequestKey("track", it.value)
                        },
                        onSaveTrackMetadata = onSaveTrackMetadata,
                        onPlay = { _, _ -> },
                        onPlayNext = onPlayNext,
                        onAddToQueue = {},
                        onToggleFavorite = onToggleFavorite,
                        onTogglePlayback = onTogglePlayback,
                        onSeek = onSeek,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onSkipToQueueItem = onSkipToQueueItem,
                        onSkipToHistoryItem = onSkipToHistoryItem,
                        onClearPlaybackHistory = onClearPlaybackHistory,
                        onMoveQueueItem = onMoveQueueItem,
                        onRemoveFromQueue = onRemoveFromQueue,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeatMode = onCycleRepeatMode,
                        onCacheSizeChange = {},
                        onLogout = {},
                        openPlayerRequested = openPlayerRequested(),
                        onPlayerOpenRequestConsumed = onPlayerOpenRequestConsumed,
                    )
                }
            }
        }
        val hostedContent: @androidx.compose.runtime.Composable () -> Unit = {
            if (backInput != null) {
                val owner =
                    androidx.compose.runtime.remember {
                        object : androidx.navigationevent.NavigationEventDispatcherOwner {
                            override val navigationEventDispatcher =
                                androidx.navigationevent.NavigationEventDispatcher().apply {
                                    addInput(backInput)
                                }
                        }
                    }
                androidx.compose.runtime.DisposableEffect(owner) {
                    onDispose { owner.navigationEventDispatcher.removeInput(backInput) }
                }
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner provides
                        owner,
                ) { content() }
            } else content()
        }
        if (restoration != null) restoration.setContent(hostedContent)
        else compose.setContent(hostedContent)
    }
}

/** Keep virtual test windows visible without changing the device display or fold state. */
@androidx.compose.runtime.Composable
private fun MusicTestWindow(
    size: androidx.compose.ui.unit.DpSize?,
    fontScale: Float,
    content: @androidx.compose.runtime.Composable () -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        androidx.compose.ui.Modifier.fillMaxSize().safeDrawingPadding(),
    ) {
        val originalDensity = androidx.compose.ui.platform.LocalDensity.current
        val fitDensity =
            if (size == null) originalDensity.density
            else
                minOf(
                    originalDensity.density,
                    constraints.maxWidth / size.width.value,
                    constraints.maxHeight / size.height.value,
                )
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(fitDensity, fontScale),
        ) {
            androidx.compose.foundation.layout.Box(
                if (size == null) androidx.compose.ui.Modifier
                else
                    androidx.compose.ui.Modifier.requiredSize(
                        (kotlin.math.ceil(size.width.value * fitDensity) / fitDensity).dp,
                        (kotlin.math.ceil(size.height.value * fitDensity) / fitDensity).dp,
                    ),
            ) { content() }
        }
    }
}
