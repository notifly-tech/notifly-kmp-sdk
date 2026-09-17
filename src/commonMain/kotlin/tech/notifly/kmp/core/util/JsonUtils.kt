package tech.notifly.kmp.core.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Converts nulls, strings, booleans, finite numbers, lists, and string-keyed maps to JSON values.
 *
 * Integer precision is preserved without rounding through a floating-point conversion. Unsupported
 * values and circular references throw [IllegalArgumentException]; repeated non-circular references are valid.
 */
internal fun Any?.toJsonElement(): JsonElement = encodeJsonValue(this, mutableListOf())

private fun encodeJsonValue(
    value: Any?,
    ancestors: MutableList<Any>,
): JsonElement =
    when (value) {
        null -> {
            JsonNull
        }

        is String -> {
            JsonPrimitive(value)
        }

        is Boolean -> {
            JsonPrimitive(value)
        }

        is Number -> {
            require(value.toDouble().isFinite())
            JsonPrimitive(value)
        }

        is Map<*, *> -> {
            withJsonAncestor(value, ancestors) {
                JsonObject(
                    value.entries.associate { (key, item) ->
                        require(key is String)
                        key to encodeJsonValue(item, ancestors)
                    },
                )
            }
        }

        is List<*> -> {
            withJsonAncestor(value, ancestors) {
                JsonArray(value.map { encodeJsonValue(it, ancestors) })
            }
        }

        else -> {
            throw IllegalArgumentException("Unsupported JSON value")
        }
    }

/** Detects cycles by identity along the current branch, releasing the ancestor even when conversion fails. */
internal inline fun <T> withJsonAncestor(
    value: Any,
    ancestors: MutableList<Any>,
    convert: () -> T,
): T {
    require(ancestors.none { it === value })
    ancestors.add(value)
    return try {
        convert()
    } finally {
        ancestors.removeAt(ancestors.lastIndex)
    }
}
