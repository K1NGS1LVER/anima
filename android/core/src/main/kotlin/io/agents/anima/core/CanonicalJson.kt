package io.agents.anima.core

import java.util.Locale
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

object CanonicalJson {
    /**
     * Serializes any object to a canonical JSON string.
     * Arrays of objects that have an 'id' property are sorted by 'id'.
     * Keys are sorted alphabetically.
     * Floats are formatted to 4 decimal places using Locale.US.
     * Nulls are omitted if possible (depending on budget rules), but for canonical JSON,
     * it's safer to just omit nulls entirely as per the size budget rule.
     */
    fun toJson(obj: Any?): String {
        return serialize(obj)
    }

    private fun serialize(obj: Any?): String {
        if (obj == null) return "null"
        
        when (obj) {
            is String -> return "\"" + escapeString(obj) + "\""
            is Number -> {
                if (obj is Float || obj is Double) {
                    val dVal = obj.toDouble()
                    if (dVal % 1.0 == 0.0) {
                        return dVal.toLong().toString()
                    }
                    return String.format(Locale.US, "%.4f", dVal).trimEnd('0').trimEnd('.')
                }
                return obj.toString()
            }
            is Boolean -> return obj.toString()
            is List<*> -> {
                val items = obj.filterNotNull()
                // Check if items have an 'id' property to sort them
                val sortedItems = if (items.isNotEmpty() && hasIdProperty(items.first())) {
                    items.sortedBy { getIdValue(it) }
                } else {
                    items
                }
                return "[" + sortedItems.joinToString(",") { serialize(it) } + "]"
            }
            is Map<*, *> -> {
                val sortedKeys = obj.keys.map { it.toString() }.sorted()
                val entries = sortedKeys.mapNotNull { key ->
                    val value = obj[key]
                    if (value == null) null else "\"${escapeString(key)}\":" + serialize(value)
                }
                return "{" + entries.joinToString(",") + "}"
            }
            else -> {
                // Data class serialization using reflection
                val properties = obj::class.memberProperties
                val sortedProps = properties.sortedBy { it.name }
                val entries = sortedProps.mapNotNull { prop ->
                    val value = (prop as KProperty1<Any, *>).get(obj)
                    if (value == null) null else "\"${escapeString(prop.name)}\":" + serialize(value)
                }
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
