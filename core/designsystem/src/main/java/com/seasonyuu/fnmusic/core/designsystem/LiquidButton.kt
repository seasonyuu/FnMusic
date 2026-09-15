package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.colorControls
import com.kyant.shapes.Capsule

/** A compact Backdrop button matching the catalog LiquidButton material recipe. */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = FnNavigationSurface,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = rememberLiquidInteraction()
    val glass = currentLiquidGlassMaterial()

    Row(
        modifier
            .then(if (glass.enabled) Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    colorControls(brightness = glass.brightness, saturation = 1.5f)
                    blur(1f.dp.toPx() * glass.blurScale)
                    lens(4.dp.toPx(), 6.dp.toPx())
                },
                layerBlock = if (isInteractive) interaction.layerBlock else null,
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = glass.surfaceAlpha))
                    }
                    if (surfaceColor.isSpecified) drawRect(surfaceColor.copy(alpha = glass.surfaceAlpha))
                },
            ) else Modifier.graphicsLayer { if (isInteractive) interaction.layerBlock(this) }
                .clip(Capsule()).drawBehind {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = glass.surfaceAlpha))
                    }
                    if (surfaceColor.isSpecified) drawRect(surfaceColor.copy(alpha = glass.surfaceAlpha))
                })
            .then(if (!glass.enabled) Modifier.liquidSurfaceHighlight() else Modifier)
            .clickable(
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(
                if (isInteractive && enabled) {
                    interaction.modifier
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
