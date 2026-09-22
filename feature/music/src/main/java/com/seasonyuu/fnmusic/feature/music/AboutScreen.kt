package com.seasonyuu.fnmusic.feature.music

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.seasonyuu.fnmusic.core.designsystem.*
import com.seasonyuu.fnmusic.core.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun rememberAboutLinkOpener(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url: String ->
            val uri = Uri.parse(url)
            if (uri.scheme == "https" || uri.scheme == "http") {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "未找到可打开链接的浏览器", Toast.LENGTH_LONG).show()
                } catch (_: SecurityException) {
                    Toast.makeText(context, "无法打开链接，请检查浏览器设置", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@Composable
private fun AboutPage(title: String, tag: String, onBack: () -> Unit, content: @Composable (Dp) -> Unit) {
    Box(Modifier.fillMaxSize().testTag(tag)) {
        // Keep the scroll viewport edge-to-edge. Only its initial content is inset;
        // CollectionPage records that content below the full status/app-bar blur.
        CollectionPage(title, onBack, headingGone = { true }) { _, top -> content(top) }
    }
}

@Composable
internal fun AboutScreen(actions: AboutActions, onLibraries: () -> Unit, onBack: () -> Unit) {
    val update by actions.update.collectAsState()
    val openLink = rememberAboutLinkOpener()
    val context = LocalContext.current
    val icon = remember(context) { context.packageManager.getApplicationIcon(context.applicationInfo).toBitmap(192, 192).asImageBitmap() }
    AboutPage("关于", "about-page", onBack) { top ->
        LazyColumn(Modifier.fillMaxSize(),
            contentPadding = edgeToEdgeContentPadding(horizontal = 20.dp, top = top + 8.dp, bottom = 20.dp, includeTopInset = false),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(icon, "FnMusic 应用图标", Modifier.size(80.dp))
                    Text("FnMusic", style = MaterialTheme.typography.headlineMedium)
                    SelectionContainer { Text("${actions.info.versionName} (${actions.info.versionCode})", color = FnTextSecondary) }
                    if (actions.info.developmentBuild) Text("开发版", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            item {
                SettingsCard {
                    Text("你的音乐，随时聆听", style = MaterialTheme.typography.titleMedium)
                    Text("FnMusic 是面向飞牛 OS 音乐服务的非官方 Android 客户端，让你随时浏览、收藏和聆听自己的音乐。",
                        style = MaterialTheme.typography.bodyMedium, color = FnTextSecondary)
                }
            }
            item {
                SettingsNavigationGroup(listOf(
                    SettingsEntry(if (update == AppUpdateState.Checking) "正在检查更新…" else "检查更新",
                        if (update == AppUpdateState.Checking) null else actions::checkUpdate),
                    SettingsEntry("源代码仓库", { openLink(PROJECT_URL) }),
                    SettingsEntry("开源许可", onLibraries),
                ))
            }
            when (val result = update) {
                AppUpdateState.Checking -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                is AppUpdateState.Failed -> item {
                    SettingsCard {
                        Text(result.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = actions::checkUpdate, colors = readableTextButtonColors()) { Text("重试") }
                        TextButton(onClick = { openLink(RELEASES_URL) }, colors = readableTextButtonColors()) { Text("打开发布列表") }
                    }
                }
                is AppUpdateState.Available -> item {
                    val release = result.release
                    SettingsCard {
                        Text(when {
                            release.newer -> "发现新版本 ${release.version}"
                            actions.info.developmentBuild -> "最新正式版 ${release.version}"
                            else -> "当前已是最新版本"
                        }, style = MaterialTheme.typography.titleMedium)
                        if (!release.newer && actions.info.developmentBuild) Text("当前为开发构建，正式版供参考。", color = FnTextSecondary)
                        val date = remember(release.publishedAt) {
                            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault()).format(Instant.parse(release.publishedAt))
                        }
                        Text("正式版 ${release.version} · $date", color = FnTextSecondary)
                        SelectionContainer { Text(release.notes.ifBlank { "此版本未提供更新说明。" }, style = MaterialTheme.typography.bodyMedium) }
                        TextButton(onClick = { openLink(release.url) }, colors = readableTextButtonColors()) {
                            Text(if (release.newer) "前往下载" else "查看正式版")
                        }
                    }
                }
                AppUpdateState.Idle -> Unit
            }
        }
    }
}

@Composable
internal fun OpenSourceLibrariesScreen(actions: AboutActions, onLibrary: (String) -> Unit, onBack: () -> Unit) {
    val state by actions.openSource.collectAsState()
    LaunchedEffect(actions) { actions.loadLibraries() }
    AboutPage("开源许可", "open-source-page", onBack) { top ->
        when (val result = state) {
            is OpenSourceState.Ready -> {
                val libraries = result.libraries
                LazyColumn(Modifier.fillMaxSize().testTag("open-source-list"),
                    contentPadding = edgeToEdgeContentPadding(horizontal = 20.dp, top = top, bottom = 20.dp, includeTopInset = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text("感谢这些开源项目 · ${libraries.size} 项", color = FnTextSecondary, style = MaterialTheme.typography.bodySmall) }
                    if (libraries.isEmpty()) item { Text("暂无开源库资料") }
                    items(libraries, key = { it.id }) { library ->
                        SettingsCard(onClick = { onLibrary(library.id) }) {
                            Text(library.name, style = MaterialTheme.typography.titleMedium)
                            if (library.version.isNotBlank()) Text(library.version, color = FnTextSecondary, style = MaterialTheme.typography.bodySmall)
                            Text(library.licenses.joinToString(" · ") { it.name }.ifBlank { "许可信息待补充" },
                                color = FnTextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            else -> LibraryLoadStatus(result, actions::loadLibraries, top)
        }
    }
}

@Composable
internal fun OpenSourceDetailScreen(actions: AboutActions, libraryId: String?, onBack: () -> Unit) {
    val state by actions.openSource.collectAsState()
    val openLink = rememberAboutLinkOpener()
    LaunchedEffect(actions) { actions.loadLibraries() }
    AboutPage("许可详情", "open-source-detail-page", onBack) { top ->
        when (val result = state) {
            is OpenSourceState.Ready -> {
                val library = result.libraries.find { it.id == libraryId }
                LazyColumn(Modifier.fillMaxSize().testTag("open-source-detail-list"),
                    contentPadding = edgeToEdgeContentPadding(horizontal = 20.dp, top = top + 8.dp, bottom = 20.dp, includeTopInset = false),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (library == null) item { Text("此依赖已不在当前版本中，请返回列表查看。") }
                    else {
                        item {
                            SettingsCard {
                                Text(library.name, style = MaterialTheme.typography.titleLarge)
                                if (library.version.isNotBlank()) Text(library.version, color = FnTextSecondary)
                                if (library.authors.isNotBlank()) Text(library.authors, color = FnTextSecondary)
                                if (library.description.isNotBlank()) Text(library.description, style = MaterialTheme.typography.bodyMedium)
                                library.website?.let { url -> TextButton(onClick = { openLink(url) }, colors = readableTextButtonColors()) { Text("项目主页") } }
                            }
                        }
                        items(library.licenses) { license ->
                            SettingsCard {
                                Text(license.name, style = MaterialTheme.typography.titleMedium)
                                SelectionContainer { Text(license.text.ifBlank { "此依赖未提供许可正文。" }, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
            else -> LibraryLoadStatus(result, actions::loadLibraries, top)
        }
    }
}

@Composable
private fun LibraryLoadStatus(state: OpenSourceState, retry: () -> Unit, top: Dp) {
    Column(Modifier.fillMaxWidth().padding(edgeToEdgeContentPadding(horizontal = 20.dp, top = top, bottom = 20.dp, includeTopInset = false)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state == OpenSourceState.Loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("正在读取本地许可…", color = FnTextSecondary)
        } else {
            Text("许可资料读取失败，请重试", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = retry, colors = readableTextButtonColors()) { Text("重试") }
        }
    }
}
