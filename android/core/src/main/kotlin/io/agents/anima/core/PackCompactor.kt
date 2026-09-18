package io.agents.anima.core

object PackCompactor {
    /**
     * String interning for repeated labels (not necessarily JVM intern, just object reuse)
     */
    private val stringPool = mutableMapOf<String, String>()
    
    private fun internStr(s: String?): String? {
        if (s == null) return null
        return stringPool.getOrPut(s) { s }
    }
    
    /**
     * Omission of empty fields is handled by the canonical json encoder when writing to file, 
     * but we can clear empty collections here.
     */
    fun compact(pack: KnowledgePack): KnowledgePack {
        stringPool.clear()
        
        val compactedScreens = pack.screens.map { screen ->
            screen.copy(
                name = internStr(screen.name),
                purpose = internStr(screen.purpose),
                kind = internStr(screen.kind),
                elements = screen.elements.map { el ->
                    el.copy(
                        role = internStr(el.role) ?: el.role,
                        label = internStr(el.label),
                        semantic = internStr(el.semantic),
                        // Drop bounds_rel precision beyond 4 dp
                        bounds_rel = el.bounds_rel?.map { Math.round(it * 10000.0) / 10000.0 }
                    )
                }
                // webp formatting is handled at the file level
            )
        }
        
        // component deduplication (if identical properties)
        val uniqueComponents = mutableListOf<ComponentInfo>()
        if (pack.design_system != null) {
            val seen = mutableSetOf<String>()
            for (comp in pack.design_system.components) {
                val key = "${comp.name}|${comp.fill}|${comp.text}|${comp.radius_dp}|${comp.height_dp}"
                if (seen.add(key)) {
                    uniqueComponents.add(comp)
                }
            }
        }
        
        return pack.copy(
            screens = compactedScreens,
            design_system = pack.design_system?.copy(
                components = uniqueComponents
            )
        )
    }
}
