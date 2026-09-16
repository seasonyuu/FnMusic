package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class LiquidDragDeformationTest {
    @Test fun preservesButtonPressAndDirectionalStretch() {
        val size = Size(200f, 200f)
        val rest = liquidDragDeformation(size, Offset.Zero, 0f, 4f)
        assertEquals(Offset.Zero, rest.translation)
        assertEquals(Offset(1f, 1f), rest.scale)
        val horizontal = liquidDragDeformation(size, Offset(100f, 0f), 1f, 4f)
        assertEquals(1.03f, horizontal.scale.x, .000001f)
        assertEquals(1.02f, horizontal.scale.y, .000001f)
        assertEquals(4.9989586f, horizontal.translation.x, .00001f)
        val vertical = liquidDragDeformation(size, Offset(0f, -100f), 1f, 4f)
        assertEquals(horizontal.scale.x, vertical.scale.y, .000001f)
        assertEquals(horizontal.scale.y, vertical.scale.x, .000001f)
        assertEquals(-horizontal.translation.x, vertical.translation.y, .000001f)
    }

    @Test fun translationResistsExtremeDragsAndZeroSizeIsSafe() {
        val extreme = liquidDragDeformation(Size(200f, 300f), Offset(100000f, -100000f), 1f, 4f)
        assertTrue(extreme.translation.x <= 200f && extreme.translation.y >= -200f)
        assertTrue(extreme.scale.x.isFinite() && extreme.scale.y.isFinite())
        val empty = liquidDragDeformation(Size.Zero, Offset(100f, 100f), 1f, 4f)
        assertEquals(Offset.Zero, empty.translation)
        assertEquals(Offset(1f, 1f), empty.scale)
    }
}
