package com.seasonyuu.fnmusic.car

import com.seasonyuu.fnmusic.core.model.ProjectionDisplay

/** Tracks connection lifetimes without relying on hidden Display.getUniqueId APIs. */
internal class ProjectionDisplays {
    private var nextToken = 0L
    private var current = emptyList<ProjectionDisplay>()

    fun removed(id: Int) { current = current.filterNot { it.id == id } }

    fun update(displays: List<ProjectionDisplay>): List<ProjectionDisplay> {
        current = displays.filter { it.id > 0 }.map { display ->
            val previous = current.firstOrNull { it.id == display.id && it.name == display.name }
            display.copy(connectionToken = previous?.connectionToken ?: ++nextToken)
        }.sortedWith(compareByDescending<ProjectionDisplay> { it.recommended }
            .thenByDescending { it.carDisplay }.thenBy { it.id })
        return current
    }

    fun contains(target: ProjectionDisplay): Boolean = current.any {
        it.id == target.id && it.name == target.name &&
            it.connectionToken == target.connectionToken && it.available
    }
}
