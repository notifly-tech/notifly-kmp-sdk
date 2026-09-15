@file:OptIn(ExperimentalJsExport::class)
package tech.notifly.kmp.popup.model

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class PopupRenderInput(
    val templateRenderingMode: String?,
    val campaignId: String?,
    val notiflyUserId: String?,
    val deviceId: String?,
    val eventName: String?,
    val eventParamsJson: String?,
)
