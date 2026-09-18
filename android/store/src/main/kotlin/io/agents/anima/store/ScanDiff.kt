package io.agents.anima.store

import io.agents.anima.core.ScreenInfo

data class ScreenDiffResult(
    val addedScreens: List<ScreenInfo>,
    val removedScreens: List<ScreenInfo>,
    val changedScreens: List<ScreenInfo>
)

object ScanDiff {
    /**
     * Compares two lists of screens (from consecutive scans).
     * Two screens are identical if their IDs match and their canonical JSON
     * representation matches (or just the fields required by the stability rule).
     * For now, we will compare ID to find additions/removals, and canonical JSON for changes.
     */
    fun diff(oldScreens: List<ScreenInfo>, newScreens: List<ScreenInfo>): ScreenDiffResult {
        val oldMap = oldScreens.associateBy { it.id }
        val newMap = newScreens.associateBy { it.id }
        
        val added = mutableListOf<ScreenInfo>()
        val removed = mutableListOf<ScreenInfo>()
        val changed = mutableListOf<ScreenInfo>()
        
        for (newScreen in newScreens) {
            val oldScreen = oldMap[newScreen.id]
            if (oldScreen == null) {
                added.add(newScreen)
            } else {
                // In actual implementation, compare structural elements.
                // Assuming ScreenInfo's equals handles the deep comparison properly 
                // (which data class does, if all nested elements are also data classes).
                if (oldScreen != newScreen) {
                    changed.add(newScreen)
                }
            }
        }
        
        for (oldScreen in oldScreens) {
            if (!newMap.containsKey(oldScreen.id)) {
                removed.add(oldScreen)
            }
        }
        
        return ScreenDiffResult(added, removed, changed)
    }
}
