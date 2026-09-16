package tech.notifly.kmp.core.util

import kotlin.js.jsTypeOf

/**
 * Converts a JavaScript plain object and its nested objects and arrays to Kotlin collections.
 *
 * Strings, booleans, numbers, and nested nulls are preserved. Numeric finiteness is checked by
 * [toJsonElement] during encoding. Unsupported types and cycles throw [IllegalArgumentException];
 * exceptions from native getters propagate to the caller.
 */
internal fun jsObjectToMap(value: dynamic): Map<String, Any?> {
    require(isPlainObject(value))
    return convertObject(value, mutableListOf())
}

/** Recognizes native Object prototypes across realms without accepting custom class instances. */
private fun isPlainObject(value: dynamic): Boolean {
    if (value == null || jsTypeOf(value) != "object") return false
    val prototype = js("Object.getPrototypeOf(value)")
    if (prototype == null) return true
    return js("Object.prototype.hasOwnProperty.call(prototype, 'constructor')") as Boolean &&
        jsTypeOf(prototype.constructor) == "function" &&
        js("Function.prototype.toString.call(prototype.constructor)") == js("Function.prototype.toString.call(Object)")
}

private fun convertValue(
    value: dynamic,
    ancestors: MutableList<Any>,
): Any? =
    when (jsTypeOf(value)) {
        "string", "boolean", "number" -> {
            value
        }

        "object" -> {
            when {
                value == null -> {
                    null
                }

                js("Array.isArray(value)") as Boolean -> {
                    withJsonAncestor(value, ancestors) {
                        List(value.length as Int) { index -> convertValue(value[index], ancestors) }
                    }
                }

                isPlainObject(value) -> {
                    convertObject(value, ancestors)
                }

                else -> {
                    throw IllegalArgumentException("Unsupported JavaScript object")
                }
            }
        }

        else -> {
            throw IllegalArgumentException("Unsupported JavaScript value")
        }
    }

private fun convertObject(
    value: dynamic,
    ancestors: MutableList<Any>,
): Map<String, Any?> =
    withJsonAncestor(value, ancestors) {
        val keys = js("Object.keys(value)") as Array<String>
        keys.associateWith { key -> convertValue(value[key], ancestors) }
    }
