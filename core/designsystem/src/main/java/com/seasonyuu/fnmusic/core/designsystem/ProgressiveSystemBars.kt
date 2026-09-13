package com.seasonyuu.fnmusic.core.designsystem

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect

/**
 * Lets the whole scene render edge-to-edge, then samples it into non-interactive
 * progressive blur layers over the system bars. Interactive content must still
 * provide scrollable content padding so its first and last items remain reachable.
 */
@Composable
fun FnProgressiveSystemBars(
    modifier: Modifier = Modifier,
    showTopBlur: Boolean = true,
    content: @Composable () -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    androidx.compose.runtime.CompositionLocalProvider(LocalFnBackdrop provides backdrop) {
        Box(modifier.fillMaxSize()) {
            ProgressiveBarBlur(
                backdrop = backdrop,
                top = false,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            content()
            if (showTopBlur) {
                ProgressiveBarBlur(
                    backdrop = backdrop,
                    top = true,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
fun ProgressiveBarBlur(
    backdrop: Backdrop,
    top: Boolean,
    modifier: Modifier,
    tint: Color = if (top) FnBackgroundTop else FnBackgroundBottom,
) {
    val density = LocalDensity.current
    val inset = with(density) {
        if (top) WindowInsets.statusBars.getTop(this).toDp()
        else WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val fallbackGradient = if (top) {
        Brush.verticalGradient(listOf(tint.copy(alpha = 0.94f), Color.Transparent))
    } else {
        Brush.verticalGradient(listOf(Color.Transparent, tint.copy(alpha = 0.94f)))
    }
    val supportsProgressiveBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val transitionHeight = if (top) 32.dp else 72.dp

    Box(
        modifier
            .fillMaxWidth()
            .height(inset + transitionHeight)
            .clearAndSetSemantics {}
            .testTag(if (top) "top-system-bar-blur" else "bottom-system-bar-blur")
            .then(
                if (supportsProgressiveBlur) {
                    Modifier.drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = {
                            blur(20.dp.toPx())
                            runtimeShaderEffect(
                                if (top) "FnTopSystemBarMask" else "FnBottomSystemBarMask",
                                progressiveMaskShader(top),
                                "content",
                            ) {
                                setFloatUniform("size", size.width, size.height)
                                setColorUniform("tint", tint)
                                setFloatUniform("tintIntensity", 0.32f)
                            }
                        },
                    )
                } else {
                    Modifier.background(fallbackGradient)
                }
            ),
    )
}

private fun progressiveMaskShader(top: Boolean): String =
    """
        uniform shader content;
        uniform float2 size;
        layout(color) uniform half4 tint;
        uniform float tintIntensity;

        half4 main(float2 coord) {
            half strength = ${if (top) "half(1.0 - smoothstep(size.y * 0.22, size.y, coord.y))" else "half(smoothstep(0.0, size.y * 0.78, coord.y))"};
            half4 glass = mix(content.eval(coord), tint, half(tintIntensity));
            return mix(half4(0.0), glass, strength);
        }
    """.trimIndent()
val LocalFnBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
