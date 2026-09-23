package com.seasonyuu.fnmusic

import com.seasonyuu.fnmusic.core.model.Playlist

/** Upload a selected photo before creation, then always remove its private draft copy. */
internal suspend fun createPlaylistWithCover(
    name: String,
    defaultCoverId: String?,
    photoUri: String?,
    prepare: suspend (String) -> String,
    upload: suspend (String) -> String,
    uploadDefault: suspend (String) -> String,
    discard: (String) -> Unit,
    uploadedCoverId: String? = null,
    onCoverUploaded: (String) -> Unit = {},
    create: suspend (String, String?) -> Playlist,
): Playlist {
    var photoPath: String? = null
    try {
        val defaultTemplate = defaultCoverId in (1..4).map { "playlist_default_$it" }
        val coverId = uploadedCoverId ?: when {
            photoUri != null -> {
                photoPath = prepare(photoUri)
                upload(requireNotNull(photoPath))
            }
            defaultTemplate -> uploadDefault(requireNotNull(defaultCoverId))
            else -> defaultCoverId
        }
        if (coverId != null && uploadedCoverId == null && (photoUri != null || defaultTemplate)) {
            require(coverId.isNotBlank()) { "上传响应缺少 coverId" }
            onCoverUploaded(coverId)
        }
        return create(name, coverId)
    } finally {
        photoPath?.let(discard)
    }
}
