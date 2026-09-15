package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.designsystem.FnAccent
import com.seasonyuu.fnmusic.core.designsystem.FnTextSecondary

@Composable
internal fun UserTitle(
    name: String,
    role: String?,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            name.ifBlank { "未命名用户" },
            modifier = Modifier.weight(1f),
            style = titleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (role == "admin") UserRoleBadge()
    }
}

@Composable
internal fun UserRoleBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag("admin-badge"),
        color = FnAccent.copy(alpha = 0.14f),
        contentColor = com.seasonyuu.fnmusic.core.designsystem.FnTextPrimary,
        shape = RoundedCornerShape(50),
    ) {
        Text("管理员", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun UserSubtitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = FnTextSecondary,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
