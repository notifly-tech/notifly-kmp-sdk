@file:OptIn(ExperimentalJsExport::class)

package tech.notifly.kmp.popup.model

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Popup identity and triggering event supplied by the host SDK.
 *
 * Only the exact [templateRenderingMode] value `ssr` uses the render API. Null, `static`, and unknown
 * values preserve the static path, without validating the remaining fields.
 *
 * SSR requires [campaignId], [notiflyUserId], [deviceId], and a nonblank [eventName]. Identifiers are
 * trimmed using ECMAScript whitespace rules; [eventName] is sent unchanged. [eventParams] supports
 * strings, booleans, finite numbers, nulls, lists, and nested maps with string keys. Null parameters
 * are sent as `{}`. Unsupported values or circular collections produce `invalid_request` for SSR.
 * Do not mutate the supplied collections while a render is pending.
 *
 * JavaScript callers should use `createPopupRenderInput` to convert plain objects and arrays.
 */
@JsExport
class PopupRenderInput(
    val templateRenderingMode: String?,
    val campaignId: String?,
    val notiflyUserId: String?,
    val deviceId: String?,
    val eventName: String?,
    val eventParams: Map<String, Any?>?,
) {
    /** Defers JavaScript conversion failures to the normal asynchronous render result. */
    internal var hasInvalidEventParams: Boolean = false
}
