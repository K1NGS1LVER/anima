package io.agents.anima.core

import io.agents.anima.engine.PrunedNode
import java.security.MessageDigest

class StableIdEngine : ScreenIdentifier {
    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    override fun screenId(observation: ScreenObservation): String {
        return "scr_" + signature(observation).structuralHash.substring(0, 12)
    }

    override fun elementId(screenId: String, node: PrunedNode, structuralPath: String): String {
        val identifier = if (!node.resourceId.isNullOrBlank()) node.resourceId else node.className
        val fingerprint = "$screenId|$identifier|$structuralPath"
        return "el_" + sha256(fingerprint).substring(0, 8)
    }

    override fun signature(observation: ScreenObservation): ScreenSignature {
        val resourceIds = observation.nodes.mapNotNull { it.resourceId }.filter { it.isNotBlank() }.sorted()
        val classPaths = observation.nodes.map { it.className }.filter { it.isNotBlank() }.distinct().sorted()
        
        val builder = StringBuilder()
        builder.append(resourceIds.joinToString(","))
        builder.append("|")
        builder.append(classPaths.joinToString(","))
        if (observation.activity != null) {
            builder.append("|").append(observation.activity)
        }
        
        val fingerprint = builder.toString()
        val hash = sha256(fingerprint)
        
        val anchors = if (resourceIds.size > 2) resourceIds.take(2) else resourceIds
        
        return ScreenSignature(
            structuralHash = hash,
            anchors = anchors,
            activity = observation.activity
        )
    }
}
