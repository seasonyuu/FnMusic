package com.seasonyuu.fnmusic.car

import com.seasonyuu.fnmusic.core.model.ProjectionDisplay
import org.junit.Assert.*
import org.junit.Test

class ProjectionTest {
    private fun display(id: Int, name: String = "vivo_car_screen", available: Boolean = true) =
        ProjectionDisplay(id, name, 0, 2160, 1080, available)

    @Test fun recommendsMainCarDisplayWithoutAssumingItsId() {
        val registry = ProjectionDisplays()
        val result = registry.update(listOf(
            display(0, "Built-in Screen"), display(90000, "vivo_car_screen_phone_mirror"),
            display(8, "HDMI"), display(123456), display(90002, "vivo_car_screen_3"),
        ))
        assertEquals(listOf(123456, 90000, 90002, 8), result.map { it.id })
        assertTrue(result.first().recommended)
        assertFalse(result[1].recommended)
        assertFalse(result.last().carDisplay)
    }

    @Test fun rejectsDisconnectedClosedAndReusedDisplayIds() {
        val registry = ProjectionDisplays()
        val original = registry.update(listOf(display(90002))).single()
        assertTrue(registry.contains(original))
        registry.update(listOf(display(90002, available = false)))
        assertFalse(registry.contains(original))
        registry.removed(90002)
        val replacement = registry.update(listOf(display(90002))).single()
        assertFalse(registry.contains(original))
        assertTrue(registry.contains(replacement))
        registry.update(emptyList())
        assertFalse(registry.contains(replacement))
    }

    @Test fun preservesSelectionAcrossRefreshAndResolutionChanges() {
        val registry = ProjectionDisplays()
        val original = registry.update(listOf(display(17))).single()
        val resized = registry.update(listOf(display(17).copy(width = 1080, height = 2160))).single()
        assertEquals(original.connectionToken, resized.connectionToken)
        assertTrue(registry.contains(original))
        registry.update(listOf(display(17, "different display")))
        assertFalse(registry.contains(original))
    }

    @Test fun rejectsPhoneDisplayAndDetectsAmErrorsEvenWithZeroExitCode() {
        assertThrows(IllegalArgumentException::class.java) { projectionCommand("test", 0) }
        assertThrows(IllegalArgumentException::class.java) { projectionCommand("test", -1) }
        assertNull(projectionFailure(0, "Warning: Activity not started, intent delivered.\nStatus: ok\n"))
        assertNotNull(projectionFailure(0, "Error: Activity not started, invalid display."))
        assertNotNull(projectionFailure(1, "java.lang.SecurityException: Permission Denial"))
        assertNotNull(projectionFailure(0, "Status: timeout"))
        assertNotNull(projectionFailure(0, "Starting: Intent { ... }"))
    }
}
