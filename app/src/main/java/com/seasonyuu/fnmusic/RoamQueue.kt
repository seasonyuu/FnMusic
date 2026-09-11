package com.seasonyuu.fnmusic

import com.seasonyuu.fnmusic.core.model.RoamItem
import com.seasonyuu.fnmusic.core.model.RoamWindow

/** Keeps the server's roam identifiers aligned with the Media3 playlist indices. */
internal class RoamQueue(initial: RoamWindow) {
    private val mutableItems = buildList {
        initial.current?.let(::add)
        initial.next?.takeUnless { next -> any { it.roamId == next.roamId } }?.let(::add)
    }.toMutableList()

    val items: List<RoamItem>
        get() = mutableItems

    /** Rehydrates the full server-id window saved alongside a playback queue. */
    internal constructor(items: List<RoamItem>) : this(RoamWindow()) {
        mutableItems += items
    }

    fun anchorAt(index: Int): RoamItem? = mutableItems.getOrNull(index)

    /**
     * Merges the result of roam-next(relativeRoamId = anchor.roamId).
     * The response current is normally already the cached successor, so only genuinely
     * new roam items are returned for appending to Media3.
     */
    fun mergeForward(anchorIndex: Int, window: RoamWindow): List<RoamItem> {
        if (anchorIndex !in mutableItems.indices) return emptyList()
        val knownRoamIds = mutableItems.mapTo(mutableSetOf(), RoamItem::roamId)
        return buildList {
            listOfNotNull(window.current, window.next).forEach { item ->
                if (knownRoamIds.add(item.roamId)) {
                    mutableItems += item
                    add(item)
                }
            }
        }
    }
}
