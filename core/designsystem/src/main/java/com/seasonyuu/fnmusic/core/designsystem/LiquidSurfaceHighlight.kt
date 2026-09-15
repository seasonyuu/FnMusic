package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Edge lighting for solid Liquid controls; independent of backdrop sampling and refraction. */
internal fun Modifier.liquidSurfaceHighlight(): Modifier = drawWithContent {
    drawContent()
    val width = 1.dp.toPx()
    val inset = width / 2f
    if (size.minDimension > width) {
        drawRoundRect(
            brush = Brush.linearGradient(
                0f to Color.White.copy(alpha = .8f),
                .45f to Color.White.copy(alpha = .12f),
                1f to Color.White.copy(alpha = .5f),
                start = Offset.Zero, end = Offset(size.width, size.height),
            ),
            topLeft = Offset(inset, inset),
            size = Size(size.width - width, size.height - width),
            cornerRadius = CornerRadius((size.minDimension - width) / 2f),
            style = Stroke(width),
        )
    }
}
