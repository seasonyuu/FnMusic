package com.seasonyuu.fnmusic.feature.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
    @Test fun developmentBuildShowsStableReleaseAsReference() {
        assertEquals("发现正式版 0.1.0", releaseTitle("0.1.0", newer = true, developmentBuild = true))
        assertEquals("最新正式版 0.1.0", releaseTitle("0.1.0", newer = false, developmentBuild = true))
        assertEquals("发现新版本 0.1.0", releaseTitle("0.1.0", newer = true, developmentBuild = false))
        assertEquals("当前已是最新版本", releaseTitle("0.1.0", newer = false, developmentBuild = false))
    }

    @Test fun releaseNotesPreviewRemovesRawMarkdownAndChangelogUrl() {
        val notes = """
            ## 本次更新
            - 支持 **AirPlay** 输出
            - 支持 [在线歌词搜索](https://example.com/lyrics)
            **Full Changelog**: https://github.com/seasonyuu/FnMusic/commits/v0.1.0
        """.trimIndent()
        assertEquals("• 支持 AirPlay 输出", releaseNotesPreview(notes))
        assertEquals("", releaseNotesPreview("**Full Changelog**: https://github.com/seasonyuu/FnMusic/commits/v0.1.0"))
    }

    @Test fun releaseNotesPreviewUsesTheIntroWhenAvailable() {
        assertEquals("支持 AirPlay 和外部在线歌词搜索。", releaseNotesPreview("""
            支持 AirPlay 和外部在线歌词搜索。

            本次亮点：
            • AirPlay 输出：连接接收设备。
        """.trimIndent()))
    }
}
