package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.model.AppearancePreference

@Composable
internal fun AppearanceSettingsScreen(
    state: MusicUiState,
    onDisplayMode: suspend (AppearancePreference) -> Unit,
    onThemeColor: suspend (com.seasonyuu.fnmusic.core.model.ThemeColorPreference) -> Unit,
    onLiquidGlass: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Box(Modifier.padding(horizontal = 20.dp)) { PageTitle("外观", onBack) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(
            edgeToEdgeContentPadding(horizontal = 20.dp, top = 20.dp, bottom = 20.dp, includeTopInset = false),
        )) {
            DisplayModePicker(state.appearance, onDisplayMode)
            Spacer(Modifier.height(20.dp))
            SettingsNavigationGroup(listOf(
                SettingsEntry("主题色", null, description = {
                    ThemeColorPicker(state.themeColor, onThemeColor)
                }),
                SettingsEntry("Liquid Glass", onLiquidGlass, when {
                    !state.liquidGlassEnabled -> "已关闭"
                    state.liquidGlassBlur < com.seasonyuu.fnmusic.core.model.LiquidGlassBlur.Default -> "更透明"
                    state.liquidGlassBlur > com.seasonyuu.fnmusic.core.model.LiquidGlassBlur.Default -> "色调更深"
                    else -> "默认"
                }),
            ))
        }
    }
}
