@file:OptIn(ExperimentalJsExport::class)
package tech.notifly.kmp.popup.model

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class PopupRendererConfig(val projectId: String, val baseUrl: String, val sdkVersion: String)
