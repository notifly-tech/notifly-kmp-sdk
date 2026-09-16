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
 *
 * Read this object inside the render callback; callers do not construct it directly. These examples
 * inspect failures only. The host must also handle the other [outcome] values, including `static`.
 *
 * Android / Kotlin (`output` is the callback argument):
 * ```kotlin
 * if (output.outcome == "failed") {
 *     println("${output.errorCode}: ${output.httpStatus}")
 * }
 * ```
 *
 * iOS / Swift (`Int?` is exported as an optional Kotlin number):
 * ```swift
 * if output.outcome == "failed" {
 *     let status: Int? = output.httpStatus?.intValue
 *     print(output.errorCode ?? "unknown", status as Any)
 * }
 * ```
 *
 * JavaScript (`output` is the callback argument):
 * ```javascript
 * if (output.outcome === "failed") {
 *   console.log(output.errorCode, output.httpStatus);
 * }
 * ```
 */
@JsExport
class PopupRenderOutput internal constructor(
    val outcome: String,
    val html: String?,
    val errorCode: String?,
    val httpStatus: Int?,
)
