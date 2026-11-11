@file:OptIn(kotlin.js.ExperimentalJsExport::class)

package tech.notifly.kmp.user

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.js.JsExport

/** Shared custom user property key policy, without logging or changing host SDK state. */
@JsExport
object UserPropertyKeyPolicy {
    private val reservedKeys = setOf("\$email", "\$phone_number", "\$timezone", "\$line_user_id", "\$appsflyer_id")

    /**
     * Returns a warning for unsupported top-level [keys], or null when none need a warning.
     *
     * Custom keys must be nonempty and contain only ASCII letters, digits, or underscores.
     * The exact reserved keys `$email`, `$phone_number`, `$timezone`, `$line_user_id`, and `$appsflyer_id`
     * are exempt. Invalid keys are sorted and JSON-escaped; input order and contents are unchanged.
     * Only keys are accepted: property values and nested objects are never inspected.
     *
     * Hosts call this after their existing timezone filtering and before dispatching the update.
     * They log the returned message and continue their normal flow without modifying or rejecting keys.
     * No history is retained, so repeated invalid keys produce a warning on every call.
     */
    fun warningMessage(keys: Array<String>): String? {
        val invalidKeys = keys.filter { it !in reservedKeys && !isSupported(it) }.sorted()
        if (invalidKeys.isEmpty()) return null

        val keyList = JsonArray(invalidKeys.map { JsonPrimitive(it) })
        return "Invalid custom user property key(s): $keyList. " +
            "Only English letters (A-Z, a-z), digits (0-9), and underscores (_) are supported. " +
            "This warning does not block the operation."
    }

    private fun isSupported(key: String): Boolean =
        key.isNotEmpty() && key.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' }
}
