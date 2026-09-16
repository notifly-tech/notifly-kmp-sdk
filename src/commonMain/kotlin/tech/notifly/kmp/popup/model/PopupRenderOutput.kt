@file:OptIn(ExperimentalJsExport::class)

package tech.notifly.kmp.popup.model

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Terminal result of a popup render request.
 *
 * [outcome] is `static` for the host's existing rendering path, `rendered` for server-rendered HTML,
 * `skipped` for HTTP 204, `failed` for an error, or `cancelled` when cancellation wins.
 * [html] is present only for `rendered`, and [errorCode] only for `failed`.
 * [httpStatus] is 200 for `rendered`, 204 for `skipped`, or the status of a classified HTTP failure.
 * It is null for local validation and transport failures, and for `static` and `cancelled` results.
 */
@JsExport
class PopupRenderOutput internal constructor(
    val outcome: String,
    val html: String?,
    val errorCode: String?,
    val httpStatus: Int?,
)
