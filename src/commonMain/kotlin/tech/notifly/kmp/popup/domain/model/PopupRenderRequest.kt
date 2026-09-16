package tech.notifly.kmp.popup.domain.model

internal data class PopupRenderRequest(
    val templateRenderingMode: String?,
    val campaignId: String?,
    val notiflyUserId: String?,
    val deviceId: String?,
    val eventName: String?,
    val eventParamsJson: String?,
)

internal data class ValidatedPopupRenderRequest(
    val projectId: String,
    val sdkVersion: String,
    val campaignId: String,
    val notiflyUserId: String,
    val deviceId: String,
    val eventName: String,
    val eventParamsJson: String?,
)
