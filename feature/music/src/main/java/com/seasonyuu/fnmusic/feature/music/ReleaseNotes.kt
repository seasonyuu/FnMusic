package com.seasonyuu.fnmusic.feature.music

private val fullChangelogLine = Regex("^\\*\\*Full Changelog\\*\\*:\\s*https?://\\S+$", RegexOption.IGNORE_CASE)
private val markdownLink = Regex("!?\\[([^]]+)]\\(https?://[^)]+\\)")
private val bareUrl = Regex("https?://\\S+")
private val heading = Regex("^#{1,6}\\s+")
private val bullet = Regex("^[-*+]\\s+")
private val numberedBullet = Regex("^[0-9]+\\.\\s+")

internal fun releaseTitle(version: String, newer: Boolean, developmentBuild: Boolean): String = when {
    developmentBuild && newer -> "发现正式版 $version"
    developmentBuild -> "最新正式版 $version"
    newer -> "发现新版本 $version"
    else -> "当前已是最新版本"
}

/** A single plain-text summary. The release page remains the source for full Markdown and links. */
internal fun releaseNotesPreview(markdown: String): String = markdown.lineSequence()
    .map(String::trim)
    .filter { it.isNotBlank() && it != "---" && !fullChangelogLine.matches(it) && !heading.containsMatchIn(it) }
    .map { line ->
        val isBullet = bullet.containsMatchIn(line) || numberedBullet.containsMatchIn(line)
        val text = line.removePrefix(">").trim()
            .replace(heading, "")
            .replace(bullet, "")
            .replace(numberedBullet, "")
            .replace(markdownLink, "$1")
            .replace(bareUrl, "")
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .trim().trimEnd(':', '：')
        if (isBullet && text.isNotBlank()) "• $text" else text
    }
    .firstOrNull(String::isNotBlank).orEmpty()
