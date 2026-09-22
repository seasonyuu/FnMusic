package com.seasonyuu.fnmusic.core.model

import kotlinx.coroutines.flow.StateFlow

const val PROJECT_URL = "https://github.com/seasonyuu/FnMusic"
const val RELEASES_URL = "$PROJECT_URL/releases"

data class AppAboutInfo(val versionName: String, val versionCode: Int, val developmentBuild: Boolean)

data class AppRelease(
    val version: String,
    val publishedAt: String,
    val notes: String,
    val url: String,
    val newer: Boolean,
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class Available(val release: AppRelease) : AppUpdateState
    data class Failed(val message: String) : AppUpdateState
}

data class OpenSourceLicense(val name: String, val text: String)
data class OpenSourceLibrary(
    val id: String,
    val name: String,
    val version: String,
    val authors: String,
    val website: String?,
    val licenses: List<OpenSourceLicense>,
    val description: String = "",
)

sealed interface OpenSourceState {
    data object Loading : OpenSourceState
    data class Ready(val libraries: List<OpenSourceLibrary>) : OpenSourceState
    data object Failed : OpenSourceState
}

interface AboutActions {
    val info: AppAboutInfo
    val update: StateFlow<AppUpdateState>
    val openSource: StateFlow<OpenSourceState>
    fun checkUpdate()
    fun loadLibraries()
}
