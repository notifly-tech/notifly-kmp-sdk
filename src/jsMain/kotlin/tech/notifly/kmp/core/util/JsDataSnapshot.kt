package tech.notifly.kmp.core.util

import kotlin.js.jsTypeOf

/** Reads own properties of JSON-like data without invoking accessors. */
internal fun snapshotJsData(value: dynamic): Any? = snapshot(value, mutableListOf())

private fun snapshot(
    value: dynamic,
    ancestors: MutableList<Any>,
): Any? =
    try {
        when (jsTypeOf(value)) {
            "undefined" -> null
            "string", "boolean", "number" -> value
            "object" -> if (value == null) null else snapshotObject(value, ancestors)
            else -> UnreadableValue
        }
    } catch (_: dynamic) {
        UnreadableValue
    }

private fun snapshotObject(
    value: dynamic,
    ancestors: MutableList<Any>,
): Any? {
    if (ancestors.size >= 64) return UnreadableValue
    return withJsonAncestor(value, ancestors) {
        if (js("Array.isArray")(value) as Boolean) {
            List(value.length as Int) { index -> ownData(value, index.toString(), ancestors) }
        } else {
            if (!isDataRecord(value)) return@withJsonAncestor UnreadableValue
            val keys = js("Object.getOwnPropertyNames")(value) as Array<String>
            keys.associateWith { ownData(value, it, ancestors) }
        }
    }
}

/** Permits ordinary inherited data and cross-realm objects while rejecting native collection and class instances. */
private fun isDataRecord(value: dynamic): Boolean {
    val getPrototype = js("Object.getPrototypeOf")
    val getDescriptor = js("Object.getOwnPropertyDescriptor")
    var prototype = getPrototype(value)
    repeat(64) {
        if (prototype == null) return true
        val constructor = getDescriptor(prototype, "constructor")
        if (constructor != null) {
            val type = constructor.value
            return jsTypeOf(type) == "function" &&
                js("Function.prototype.toString.call")(type) == js("Function.prototype.toString.call")(js("Object"))
        }
        prototype = getPrototype(prototype)
    }
    return false
}

private fun ownData(
    value: dynamic,
    key: String,
    ancestors: MutableList<Any>,
): Any? {
    val descriptor = js("Object.getOwnPropertyDescriptor")(value, key)
    if (descriptor == null) return null
    if (js("Object.prototype.hasOwnProperty.call")(descriptor, "value") != true) return UnreadableValue
    return snapshot(descriptor.value, ancestors)
}
