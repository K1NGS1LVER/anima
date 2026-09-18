package io.agents.anima.core

import java.util.Locale
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

object CanonicalJson {
    fun toJson(obj: Any?): String {
        return serialize(obj) ?: "null"
    }

    private fun serialize(obj: Any?): String? {
        if (obj == null) return null
        
        when (obj) {
            is String -> {
                if (obj.isEmpty()) return null
                return "\"" + escapeString(obj) + "\""
            }
            is Number -> {
                if (obj is Float || obj is Double) {
                    val dVal = obj.toDouble()
                    if (dVal % 1.0 == 0.0) {
                        return String.format(Locale.US, "%.4f", dVal)
                    }
                    return String.format(Locale.US, "%.4f", dVal)
                }
                return obj.toString()
            }
            is Boolean -> return obj.toString()
            is List<*> -> {
                val items = obj.filterNotNull()
                if (items.isEmpty()) return null
                
                val sortedItems = if (items.isNotEmpty() && hasIdProperty(items.first())) {
                    items.sortedBy { getIdValue(it) }
                } else {
                    items
                }
                val serializedItems = sortedItems.mapNotNull { serialize(it) }
                if (serializedItems.isEmpty()) return null
                return "[" + serializedItems.joinToString(",") + "]"
            }
            is Map<*, *> -> {
                if (obj.isEmpty()) return null
                val sortedKeys = obj.keys.map { it.toString() }.sorted()
                val entries = sortedKeys.mapNotNull { key ->
                    val value = obj[key]
                    val serializedValue = serialize(value)
                    if (serializedValue == null) null else "\"${escapeString(key)}\":" + serializedValue
                }
                if (entries.isEmpty()) return null
                return "{" + entries.joinToString(",") + "}"
            }
            else -> {
                val properties = obj::class.memberProperties
                val sortedProps = properties.sortedBy { it.name }
                val entries = sortedProps.mapNotNull { prop ->
                    @Suppress("UNCHECKED_CAST")
                    val value = (prop as KProperty1<Any, *>).get(obj)
                    val serializedValue = serialize(value)
                    if (serializedValue == null) null else "\"${escapeString(prop.name)}\":" + serializedValue
                }
                if (entries.isEmpty()) return null
                return "{" + entries.joinToString(",") + "}"
            }
        }
    }

    private fun hasIdProperty(obj: Any): Boolean {
        if (obj is Map<*, *>) return obj.containsKey("id")
        return obj::class.memberProperties.any { it.name == "id" }
    }

    private fun getIdValue(obj: Any): String {
        if (obj is Map<*, *>) return obj["id"].toString()
        @Suppress("UNCHECKED_CAST")
        val prop = obj::class.memberProperties.first { it.name == "id" } as KProperty1<Any, *>
        return prop.get(obj).toString()
    }

    private fun escapeString(s: String): String {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
    }
}
