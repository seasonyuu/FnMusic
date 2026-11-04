package com.seasonyuu.fnmusic.core.model

import kotlinx.coroutines.flow.StateFlow

data class ProjectionDisplay(
    val id: Int,
    val name: String,
    val connectionToken: Long,
    val width: Int,
    val height: Int,
    val available: Boolean,
) {
    // These names identify Jovi's known roles, not an assumed range of display IDs.
    val recommended: Boolean get() = name == "vivo_car_screen"
    val carDisplay: Boolean get() = name.startsWith("vivo_car_screen")
    val description: String get() = when {
        recommended -> "车机主屏 · 推荐"
        name.endsWith("phone_mirror") -> "手机镜像屏"
        name.endsWith("continuity") -> "接续屏幕"
        carDisplay -> "车机辅助屏"
        else -> "外接屏幕"
    }
}

enum class ProjectionPermission { Unavailable, Unsupported, Required, Granted }

data class CarProjectionState(
    val permission: ProjectionPermission = ProjectionPermission.Unavailable,
    val displays: List<ProjectionDisplay> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val launched: Boolean = false,
)

interface CarProjectionActions {
    val state: StateFlow<CarProjectionState>
    fun refresh()
    fun authorize()
    fun project(display: ProjectionDisplay)
}
