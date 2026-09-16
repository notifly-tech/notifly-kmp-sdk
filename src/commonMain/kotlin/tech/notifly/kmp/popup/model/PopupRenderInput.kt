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
 * trimmed using ECMAScript whitespace rules; [eventName] is sent unchanged. [eventParamsJson] must
 * encode a JSON object, with null treated as `{}`. Invalid SSR input produces `invalid_request`.
 */
@JsExport
class PopupRenderInput(
    val templateRenderingMode: String?,
    val campaignId: String?,
    val notiflyUserId: String?,
    val deviceId: String?,
    val eventName: String?,
    val eventParamsJson: String?,
)
