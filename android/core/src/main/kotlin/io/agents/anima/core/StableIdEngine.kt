package io.agents.anima.core

import java.security.MessageDigest

object StableIdEngine {
    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Compute stable screen ID.
     * canonical_fingerprint is built from structure only:
     * - sorted list of non-null resource_ids on the screen
     * - class-path skeleton sorted and deduped
     * - activity name when observable
     */
    fun computeScreenId(
        resourceIds: List<String>,
        classPaths: List<String>,
        activityName: String?
    ): Pair<String, String> {
        val sortedResourceIds = resourceIds.filter { it.isNotBlank() }.sorted()
        val sortedClassPaths = classPaths.filter { it.isNotBlank() }.distinct().sorted()
        
        val builder = StringBuilder()
        builder.append(sortedResourceIds.joinToString(","))
        builder.append("|")
        builder.append(sortedClassPaths.joinToString(","))
        if (activityName != null) {
            builder.append("|").append(activityName)
        }
        
        val fingerprint = builder.toString()
        val hash = sha256(fingerprint)
        return Pair("scr_" + hash.substring(0, 12), hash)
    }

    /**
     * element_id = "el_" + sha256(screen_id + "|" + (resource_id ?: role) + "|" + structural_path)[0:8]
     */
    fun computeElementId(
        screenId: String,
        resourceId: String?,
        role: String,
        structuralPath: String
    ): String {
        val identifier = if (!resourceId.isNullOrBlank()) resourceId else role
        val fingerprint = "$screenId|$identifier|$structuralPath"
        return "el_" + sha256(fingerprint).substring(0, 8)
    }
}
