@file:OptIn(ExperimentalJsExport::class)

package tech.notifly.kmp.popup

import tech.notifly.kmp.core.util.jsObjectToMap
import tech.notifly.kmp.popup.model.PopupRenderInput
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

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
                jsObjectToMap(eventParams)
            }
        } catch (error: dynamic) {
            invalid = true
            null
        }
    return PopupRenderInput(templateRenderingMode, campaignId, notiflyUserId, deviceId, eventName, params).also {
        it.hasInvalidEventParams = invalid
    }
}
