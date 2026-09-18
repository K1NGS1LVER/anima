package io.agents.anima.core

object PackCompactor {
    private val stringPool = mutableMapOf<String, String>()
    
    private fun internStr(s: String?): String? {
        if (s == null) return null
        return stringPool.getOrPut(s) { s }
    }
    
    fun compact(pack: KnowledgePack): KnowledgePack {
        stringPool.clear()
        
        val compactedScreens = pack.screens.map { screen ->
            screen.copy(
                name = internStr(screen.name),
                purpose = internStr(screen.purpose),
                elements = screen.elements.map { el ->
                    el.copy(
                        label = internStr(el.label),
                        semantic = internStr(el.semantic),
                        boundsRel = el.boundsRel.map { Math.round(it * 10000.0) / 10000.0 }
                    )
                }
            )
        }
        
        val uniqueComponents = mutableListOf<ComponentToken>()
        if (pack.designSystem != null) {
            val seen = mutableSetOf<String>()
            for (comp in pack.designSystem.components) {
                val key = "${comp.name}|${comp.fill}|${comp.text}|${comp.radiusDp}|${comp.heightDp}"
                if (seen.add(key)) {
                    uniqueComponents.add(comp)
                }
            }
        }
        
        return pack.copy(
            screens = compactedScreens,
            designSystem = pack.designSystem?.copy(
                components = uniqueComponents
            )
        )
    }
}
