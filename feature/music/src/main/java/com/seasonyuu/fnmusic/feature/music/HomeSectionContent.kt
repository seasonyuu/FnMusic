package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.designsystem.FnTextSecondary

/** Cached content stays visible while refreshing; placeholders are only for unknown sections. */
@Composable
internal fun HomeSectionContent(
    state: MusicUiState,
    section: CatalogSection,
    hasContent: Boolean,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    val loading = state.loading && section in state.pendingSections
    val error = state.sectionErrors[section]
    Column {
        if (error != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(error, Modifier.weight(1f).padding(vertical = 12.dp), color = FnTextSecondary, style = MaterialTheme.typography.bodySmall)
                TextButton(colors = readableTextButtonColors(), onClick = onRetry) { Text("重试") }
            }
        }
        when {
            hasContent -> content()
            loading && section !in state.loadedSections -> HomeSectionSkeleton(section)
            error == null -> Text(
                when (section) {
                    CatalogSection.Tracks -> "还没有最近添加的歌曲"
                    CatalogSection.Albums -> "还没有专辑"
                    CatalogSection.Playlists -> "还没有歌单"
                    else -> "暂无内容"
                },
                Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                color = FnTextSecondary,
            )
        }
    }
}

@Composable
private fun HomeSectionSkeleton(section: CatalogSection) {
    val semantics = Modifier.clearAndSetSemantics { contentDescription = "正在加载${section.title}" }
    if (section == CatalogSection.Tracks) {
        Column(semantics.fillMaxWidth().height(220.dp).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                Row(Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(14.dp)).background(com.seasonyuu.fnmusic.core.designsystem.FnCard).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Placeholder(Modifier.size(52.dp))
                    Column(Modifier.weight(1f).padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Placeholder(Modifier.fillMaxWidth(.58f).height(14.dp))
                        Placeholder(Modifier.fillMaxWidth(.35f).height(10.dp))
                    }
                }
            }
        }
    } else {
        LazyRow(semantics, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(4) {
                Column(Modifier.width(142.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Placeholder(Modifier.size(142.dp))
                    Placeholder(Modifier.fillMaxWidth(.7f).height(14.dp))
                    Placeholder(Modifier.fillMaxWidth(.45f).height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun Placeholder(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(com.seasonyuu.fnmusic.core.designsystem.FnTextPrimary.copy(alpha = .10f)))
}
