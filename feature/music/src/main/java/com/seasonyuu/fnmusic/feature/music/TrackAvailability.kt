package com.seasonyuu.fnmusic.feature.music

import com.seasonyuu.fnmusic.core.model.Track

internal val Track.unavailableLabel: String
    get() = when (accessStatus) {
        1, 3 -> "已失效 · 文件不存在"
        2, 4 -> "不可用 · 无访问权限"
        else -> "歌曲暂不可用"
    }
