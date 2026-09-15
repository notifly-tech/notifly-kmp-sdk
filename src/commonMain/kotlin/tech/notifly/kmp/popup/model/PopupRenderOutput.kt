@file:OptIn(ExperimentalJsExport::class)
package tech.notifly.kmp.popup.model

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class PopupRenderOutput internal constructor(
    val outcome: String, val html: String?, val errorCode: String?, val httpStatus: Int?,
)
