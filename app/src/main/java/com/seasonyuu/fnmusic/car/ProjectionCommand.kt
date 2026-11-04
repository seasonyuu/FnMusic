package com.seasonyuu.fnmusic.car

internal fun projectionCommand(packageName: String, displayId: Int): List<String> {
    require(displayId > 0) { "请选择外接屏幕" }
    return listOf(
        "/system/bin/am", "start", "-W", "--display", displayId.toString(),
        "--activity-clear-top", "--activity-single-top",
        "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
        "-n", "$packageName/com.seasonyuu.fnmusic.MainActivity",
    )
}

internal fun projectionFailure(exitCode: Int, output: String): String? = when {
    exitCode != 0 || output.lineSequence().any {
        it.trimStart().startsWith("Error", ignoreCase = true) ||
            it.contains("Exception") || it.trimStart().startsWith("Status: timeout")
    } -> "启动失败：${output.trim().take(600).ifBlank { "车机拒绝启动（$exitCode）" }}"
    !output.contains("Status: ok") -> "未能确认投屏成功，请刷新屏幕列表后重试"
    else -> null
}
