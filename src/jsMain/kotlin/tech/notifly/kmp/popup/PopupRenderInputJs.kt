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
 *
 * JavaScript-only entry point. Android/Kotlin and iOS/Swift construct [PopupRenderInput] directly.
 * With the default package name (`sdk` may also be the host SDK's existing imported module):
 *
 * ```javascript
 * const sdk = require("notifly-kmp-sdk");
 * const popup = sdk.tech.notifly.kmp.popup;
 * const input = popup.createPopupRenderInput(
 *   "ssr", "campaign-id", "user-id", "device-id", "purchase",
 *   { items: [{ id: "P1", quantity: 2 }], enabled: true, nullable: null },
 * );
 * ```
 *
 * Pass `input` to `renderer.render(input, onComplete)`. Do not JSON-stringify the event parameters.
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
