package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.seasonyuu.fnmusic.core.designsystem.*
import com.seasonyuu.fnmusic.core.model.*

@Composable
internal fun SettingsScreen(
    state: MusicUiState,
    onCacheSizeChange: (Long) -> Unit,
    onLogout: () -> Unit,
    onLiquidGlass: () -> Unit,
    onBack: (() -> Unit)? = null,
    onPassword: (() -> Unit)? = null,
    onRefreshProfile: () -> Unit = {},
    onAppearance: () -> Unit = {},
    onCache: () -> Unit = {},
    onLyrics: () -> Unit = {},
    onQuality: () -> Unit = {},
    onAdminLibraries: (() -> Unit)? = null,
    onAdminUsers: (() -> Unit)? = null,
    onAdminServer: (() -> Unit)? = null,
) {
    LaunchedEffect(Unit) { onRefreshProfile() }
    val rootPage = onBack == null
    Column(
        modifier = Modifier.fillMaxSize().testTag("settings-page")
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    if (rootPage) WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            ),
    ) {
        if (onBack != null) {
            Box(Modifier.padding(horizontal = 20.dp)) { PageTitle("设置", onBack) }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = edgeToEdgeContentPadding(
                horizontal = 20.dp,
                top = 20.dp,
                bottom = 20.dp,
                includeTopInset = rootPage,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
          item {
            SettingsCard(modifier = Modifier.testTag("profile-identity")) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Text(state.user?.name?.take(1)?.uppercase() ?: "♪", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        UserTitle(state.user?.name?.takeIf { it.isNotBlank() } ?: "音乐账户", state.user?.role)
                        UserSubtitle("当前服务器 · ${state.serverName.ifBlank { "未连接" }}")
                    }
                }
            }
          }
        state.profileError?.let { message -> item {
            Text(message, color = MaterialTheme.colorScheme.error)
            TextButton(colors = readableTextButtonColors(), onClick = onRefreshProfile) { Text("重试") }
        } }
        item {
            SettingsNavigationGroup(buildList {
                onPassword?.let { add(SettingsEntry("修改密码", it)) }
                add(SettingsEntry("外观", onAppearance))
                add(SettingsEntry("音质偏好", onQuality))
                add(SettingsEntry("自动缓存歌曲", onCache))
                add(SettingsEntry("歌词", onLyrics))

            })
        }
        if (state.user?.role == "admin") {
            val administration = buildList {
                onAdminLibraries?.let { add(SettingsEntry("音乐库管理", it)) }
                onAdminUsers?.let { add(SettingsEntry("用户管理", it)) }
                onAdminServer?.let { add(SettingsEntry("服务器设置", it)) }
            }
            if (administration.isNotEmpty()) item { SettingsNavigationGroup(administration) }
        }
        item {
            SettingsNavigationGroup(listOf(SettingsEntry("退出音乐登录", onLogout)))
        }
        }
    }
}

@Composable
internal fun LiquidGlassSettingsScreen(
    multiplier: Float,
    saveError: String?,
    onValueChange: (Float) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    enabled: Boolean = true,
    onEnabledChange: (Boolean) -> Unit = {},
) {
    CompositionLocalProvider(
        LocalLiquidGlassBlur provides multiplier,
        LocalLiquidGlassEnabled provides enabled,
        LocalContentColor provides FnTextPrimary,
    ) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            Box(Modifier.padding(horizontal = 20.dp)) { PageTitle("Liquid Glass", onBack) }
            LazyColumn(
                modifier = Modifier.weight(1f).testTag("liquid-glass-page"),
                contentPadding = edgeToEdgeContentPadding(horizontal = 20.dp, top = 20.dp, bottom = 20.dp, includeTopInset = false),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
              item {
                SettingsCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("启用 Liquid Glass", modifier = Modifier.weight(1f))
                        // The page is part of the scene backdrop. Sampling that ancestor
                        // from this toggle would create a RenderNode cycle; use its track only.
                        CompositionLocalProvider(LocalFnBackdrop provides null) {
                            LiquidToggle(
                                checked = enabled,
                                onCheckedChange = onEnabledChange,
                                modifier = Modifier.testTag("liquid-glass-toggle").semantics {
                                    contentDescription = "启用 Liquid Glass"
                                },
                            )
                        }
                    }
                }
              }
            item { LiquidGlassPreview() }
            item {
                SettingsCard(modifier = Modifier.alpha(if (enabled) 1f else 0.38f)) {
                    LiquidSlider(
                        value = LiquidGlassBlur.toSlider(multiplier),
                        enabled = enabled,
                        onValueChange = { onValueChange(LiquidGlassBlur.fromSlider(it)) },
                        onValueChangeFinished = onSave,
                        modifier = Modifier.fillMaxWidth().testTag("liquid-glass-slider").semantics {
                            contentDescription = "Liquid Glass 效果"
                            stateDescription = when {
                                multiplier <= LiquidGlassBlur.Minimum -> "透明"
                                multiplier < LiquidGlassBlur.Default -> "偏透明"
                                multiplier == LiquidGlassBlur.Default -> "默认"
                                multiplier < LiquidGlassBlur.Maximum -> "偏色调"
                                else -> "色调"
                            }
                        },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("透明", style = MaterialTheme.typography.labelMedium, color = FnTextSecondary)
                        Text("默认", style = MaterialTheme.typography.labelMedium, color = FnTextSecondary)
                        Text("色调", style = MaterialTheme.typography.labelMedium, color = FnTextSecondary)
                    }
                    OutlinedButton(colors = readableOutlinedButtonColors(),
                        enabled = enabled,
                        onClick = { onValueChange(LiquidGlassBlur.Default); onSave() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("恢复默认") }
                }
            }
            if (saveError != null) {
                item {
                    Text(saveError, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("liquid-glass-save-error"))
                    TextButton(colors = readableTextButtonColors(), onClick = onSave) { Text("重试保存") }
                }
            }
            }
        }
    }
}

@Composable
private fun LiquidGlassPreview() {
    val backdrop = rememberLayerBackdrop()
    val scrollState = rememberScrollState()
    var isPlaying by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val appIcon = remember(context) {
        val drawable = context.packageManager.getApplicationIcon(context.applicationInfo)
        val bitmap = android.graphics.Bitmap.createBitmap(168, 168, android.graphics.Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(android.graphics.Canvas(bitmap))
        bitmap.asImageBitmap()
    }
    val previewState = remember(isPlaying) {
        PlayerState(
            queue = listOf(PlayableTrack(
                track = Track(TrackId("preview"), "歌曲名", artists = listOf(Artist(ArtistId("preview"), "歌手"))),
                streamUrl = "",
            )),
            currentIndex = 0,
            playbackStatus = if (isPlaying) PlaybackStatus.Playing else PlaybackStatus.Paused,
            repeatMode = RepeatMode.All,
        )
    }
    Box(
        Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(28.dp))
            .testTag("liquid-glass-preview"),
    ) {
        // Record the moving introduction, while the glass controls stay outside this layer.
        Column(
            Modifier.fillMaxSize().then(if (LocalLiquidGlassEnabled.current) Modifier.layerBackdrop(backdrop) else Modifier)
                .verticalScroll(scrollState).testTag("liquid-glass-preview-scroll"),
        ) {
            PreviewBand(Color(0xFFFFF2CB), Color(0xFF342B3E), "飞牛音乐", "让每一首喜欢的歌，回到日常。", introduction = true)
            PreviewBand(Color(0xFFFFD4B5), Color(0xFF432D3C), "你的音乐，随心聆听", "连接自己的音乐库，发现专辑、歌手与熟悉的旋律。")
            PreviewBand(Color(0xFFF7A7B5), Color(0xFF48283D), "收藏每一次心动", "把喜欢的歌曲加入收藏，用歌单记录不同的心情。")
            PreviewBand(Color(0xFFAD9AD8), Color(0xFF2F2548), "让下一首带来惊喜", "开启随机漫游，让音乐陪你走过专注、放松与出发的时刻。")
            PreviewBand(Color(0xFF665B9C), Color.White, "跟着歌词，听见故事", "在旋律与歌词之间，重温那些值得反复聆听的片段。")
            PreviewBand(Color(0xFF302B4D), Color.White, "此刻，只管沉浸", "从第一首到下一首，把时间留给音乐。", bottomPadding = 112.dp)
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).testTag("liquid-glass-preview-player")) {
            LiquidMiniPlayer(
                state = previewState,
                backdrop = backdrop,
                onToggle = { isPlaying = !isPlaying },
                onNext = {},
                onOpenPlayer = {},
                modifier = Modifier.fillMaxWidth().height(50.dp),
                coverContent = { modifier ->
                    Image(appIcon, "应用图标", modifier.clip(RoundedCornerShape(8.dp)))
                },
            )
        }
    }
}

@Composable
private fun PreviewBand(
    background: Color,
    foreground: Color,
    title: String,
    description: String,
    introduction: Boolean = false,
    bottomPadding: androidx.compose.ui.unit.Dp = 24.dp,
) {
    Column(
        Modifier.fillMaxWidth().background(background).heightIn(min = 140.dp)
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = foreground, style = if (introduction) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium)
        Text(description, color = foreground.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun settingsCardColors() = CardDefaults.cardColors(containerColor = FnCard, contentColor = FnTextPrimary)

@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = settingsCardColors(), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}


internal data class SettingsEntry(
    val title: String,
    val onClick: (() -> Unit)?,
    val value: String? = null,
    val description: (@Composable () -> Unit)? = null,
)

@Composable
internal fun SettingsNavigationGroup(entries: List<SettingsEntry>) {
    Card(colors = settingsCardColors(), modifier = Modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = FnTextSecondary.copy(alpha = 0.12f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .then(entry.onClick?.let { Modifier.clickable(role = Role.Button, onClick = it) } ?: Modifier)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(entry.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                entry.value?.let {
                    Text(
                        it,
                        color = FnTextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                    )
                }
                if (entry.onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = FnTextSecondary)
            }
            entry.description?.let { description ->
                Box(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 12.dp)) { description() }
            }
        }
    }
}
