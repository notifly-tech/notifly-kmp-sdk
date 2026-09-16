@file:OptIn(ExperimentalJsExport::class)

package tech.notifly.kmp.popup

import tech.notifly.kmp.popup.model.PopupRenderInput
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.jsTypeOf

/**
 * Creates popup input from a JavaScript plain object, converting nested objects and arrays to Kotlin collections.
 *
 * Null or omitted [eventParams] is sent as `{}`. Nested values must be JSON-compatible; undefined,
 * functions, symbols, bigint, class instances, and circular references are invalid. Conversion errors
 * are reported by [PopupRenderer.render] as `invalid_request`, not thrown to the caller. Modes other
 * than `ssr` bypass conversion. JavaScript numbers retain their existing precision.
 */
@JsExport
fun createPopupRenderInput(
    templateRenderingMode: String?,
    campaignId: String?,
    notiflyUserId: String?,
    deviceId: String?,
    eventName: String?,
    eventParams: dynamic = null,
): PopupRenderInput {
    var invalid = false
    val params =
        try {
            if (templateRenderingMode != "ssr" || eventParams == null) {
                null
            } else {
                require(isPlainObject(eventParams))
                convertObject(eventParams, mutableListOf())
            }
        } catch (error: dynamic) {
            invalid = true
            null
        }
    return PopupRenderInput(templateRenderingMode, campaignId, notiflyUserId, deviceId, eventName, params).also {
        it.hasInvalidEventParams = invalid
    }
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
                    withAncestor(value, ancestors) {
                        List(value.length as Int) { index -> convertValue(value[index], ancestors) }
                    }
                }

                isPlainObject(value) -> {
                    convertObject(value, ancestors)
                }

                else -> {
                    throw IllegalArgumentException("Unsupported event parameter object")
                }
            }
        }

        else -> {
            throw IllegalArgumentException("Unsupported event parameter value")
        }
    }

private fun convertObject(
    value: dynamic,
    ancestors: MutableList<Any>,
): Map<String, Any?> =
    withAncestor(value, ancestors) {
        val keys = js("Object.keys(value)") as Array<String>
        keys.associateWith { key -> convertValue(value[key], ancestors) }
    }

/** Detects cycles by identity without rejecting repeated references in separate branches. */
private inline fun <T> withAncestor(
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
