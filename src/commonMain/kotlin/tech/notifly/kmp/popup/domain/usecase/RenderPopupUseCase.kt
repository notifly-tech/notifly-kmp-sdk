package tech.notifly.kmp.popup.domain.usecase

import tech.notifly.kmp.popup.domain.model.PopupRenderRequest
import tech.notifly.kmp.popup.domain.model.PopupRenderResult
import tech.notifly.kmp.popup.domain.model.PopupRenderingMode
import tech.notifly.kmp.popup.domain.model.ValidatedPopupRenderRequest
import tech.notifly.kmp.popup.domain.model.trimJsWhitespace
import tech.notifly.kmp.popup.domain.repository.PopupRenderRepository

internal class RenderPopupUseCase(private val projectId: String, private val sdkVersion: String, private val repository: PopupRenderRepository) {
    /**
     * Bypasses static requests and validates SSR configuration and identifiers before repository access.
     *
     * Dot-only user and campaign IDs are rejected because URL parsers normalize complete dot segments
     * even when percent-encoded. Event JSON validation belongs to the repository's encoding boundary.
     */
    suspend fun render(request: PopupRenderRequest): PopupRenderResult {
        if (PopupRenderingMode.from(request.templateRenderingMode) == PopupRenderingMode.STATIC) return PopupRenderResult.Static
        val header = sdkVersion.trimJsWhitespace()
        if (!Regex("[0-9a-f]{32}").matches(projectId) || header.length !in 1..64 || '\r' in sdkVersion || '\n' in sdkVersion) {
            return PopupRenderResult.Failed("invalid_configuration")
        }
        val user = request.notiflyUserId?.trimJsWhitespace()
        val device = request.deviceId?.trimJsWhitespace()
        val campaign = request.campaignId?.trimJsWhitespace()
        val event = request.eventName
        if (
            user == null || user.length !in 1..255 ||
            device == null || device.length !in 1..255 ||
            campaign == null || campaign.length !in 1..1024 ||
            event == null || event.length !in 1..255 || event.trimJsWhitespace().isEmpty()
        ) {
            return PopupRenderResult.Failed("invalid_request")
        }
        if (user == "." || user == ".." || campaign == "." || campaign == "..") {
            return PopupRenderResult.Failed("invalid_request")
        }
        return repository.render(ValidatedPopupRenderRequest(projectId, header, campaign, user, device, event, request.eventParamsJson))
    }
}
