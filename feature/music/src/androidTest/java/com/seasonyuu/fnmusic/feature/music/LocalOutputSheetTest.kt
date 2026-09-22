package com.seasonyuu.fnmusic.feature.music

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LocalOutputSheetTest {
    @get:Rule val compose=createComposeRule()
    private val speaker=LocalAudioDevice(1,"手机扬声器",LocalAudioDeviceKind.Speaker)
    private val headphones=LocalAudioDevice(2,"测试耳机",LocalAudioDeviceKind.Bluetooth)
    private class Controller(initial:OutputState):PlaybackOutputController {
        override val outputState=MutableStateFlow(initial)
        val localRequests=mutableListOf<Int>()
        var defaults=0
        override fun selectLocalOutput(deviceId:Int) {localRequests+=deviceId}
        override fun useLocalOutput() {defaults++}
    }
    private fun show(controller:Controller) {
        compose.setContent { FnMusicTheme { CompositionLocalProvider(LocalPlaybackOutput provides controller,
            LocalNetworkPermissionRequest provides { callback -> callback(false) }) { AirPlayOutputEntry() } } }
        compose.onNodeWithTag("player-airplay").performClick()
    }
    @Test fun localDevicesRemainUsableWhenLanPermissionIsDeniedAndSelectionIsNotOptimistic() {
        val controller=Controller(OutputState(local=LocalAudioState(listOf(speaker,headphones),currentId=1)))
        show(controller)
        compose.onNodeWithTag("local-system-output").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("系统输出选择器").assertDoesNotExist()
        compose.onNodeWithTag("local-device-1").assertIsSelected()
        compose.onNodeWithTag("local-device-2").assertIsNotSelected().performClick()
        compose.runOnIdle {assertEquals(listOf(2),controller.localRequests)}
        compose.onNodeWithTag("local-device-1").assertIsSelected()
        compose.onNodeWithTag("local-device-2").assertIsNotSelected()
        compose.runOnIdle {controller.outputState.value=controller.outputState.value.copy(local=controller.outputState.value.local.copy(requestedId=2,pending=true,awaitingPlayback=true))}
        compose.onNodeWithText("待播放时确认").assertIsDisplayed()
        compose.runOnIdle {controller.outputState.value=controller.outputState.value.copy(local=controller.outputState.value.local.copy(currentId=2,pending=false))}
        compose.onNodeWithTag("local-device-2").assertIsSelected()
        compose.onNodeWithTag("local-device-1").assertIsNotSelected()
        compose.onNodeWithTag("airplay-local").performClick()
        compose.runOnIdle {assertEquals(1,controller.defaults)}
    }
    @Test fun remoteOutputDoesNotMarkInactiveLocalDeviceAndRemovedRowsDisappear() {
        val controller=Controller(OutputState(output=PlaybackOutput.AirPlay("mac","Mac"),local=LocalAudioState(listOf(speaker,headphones),currentId=2)))
        show(controller)
        compose.onNodeWithTag("local-device-2").assertIsNotSelected()
        compose.onNodeWithTag("airplay-device-mac").assertIsSelected()
        compose.runOnIdle {controller.outputState.value=OutputState(local=LocalAudioState(listOf(speaker),error="设备已断开"))}
        compose.onNodeWithTag("local-device-2").assertDoesNotExist()
        compose.onNodeWithTag("local-device-1").assertIsNotSelected()
        compose.onNodeWithTag("local-output-error").assertIsDisplayed()
    }
}
