package tech.notifly.kmp.popup.domain.usecase

import tech.notifly.kmp.core.util.isJsBlank
import tech.notifly.kmp.core.util.trimJsWhitespace
import tech.notifly.kmp.popup.domain.model.PopupRenderRequest
import tech.notifly.kmp.popup.domain.model.PopupRenderResult
import tech.notifly.kmp.popup.domain.model.PopupRenderingMode
import tech.notifly.kmp.popup.domain.model.ValidatedPopupRenderRequest
import tech.notifly.kmp.popup.domain.repository.PopupRenderRepository

internal class RenderPopupUseCase(
    private val projectId: String,
    private val sdkVersion: String,
    private val repository: PopupRenderRepository,
) {
    /**
     * Bypasses static requests and validates SSR configuration and identifiers before repository access.
     *
     * Dot-only user and campaign IDs are rejected because URL parsers normalize complete dot segments
     * even when percent-encoded. Event parameter validation belongs to the repository's encoding boundary.
     */
    suspend fun render(request: PopupRenderRequest): PopupRenderResult {
        if (PopupRenderingMode.from(request.templateRenderingMode) ==
            PopupRenderingMode.STATIC
        ) {
            return PopupRenderResult.Static
        }
        val header = parseSdkVersion(sdkVersion)
        if (!Regex("[0-9a-f]{32}").matches(projectId) || header == null) {
            return PopupRenderResult.Failed("invalid_configuration")
        }
        val user = request.notiflyUserId?.trimJsWhitespace()
        val device = request.deviceId?.trimJsWhitespace()
        val campaign = request.campaignId?.trimJsWhitespace()
        val event = request.eventName
        if (
            request.hasInvalidEventParams ||
            user == null || !isValidUserIdLength(user) ||
            device == null || !isValidDeviceIdLength(device) ||
            campaign == null || !isValidCampaignIdLength(campaign) ||
            event == null || !isValidEventNameLength(event) || event.isJsBlank()
        ) {
            return PopupRenderResult.Failed("invalid_request")
        }
        if (user == "." || user == ".." || campaign == "." || campaign == "..") {
            return PopupRenderResult.Failed("invalid_request")
        }
        return repository.render(
            ValidatedPopupRenderRequest(projectId, header, campaign, user, device, event, request.eventParams),
        )
    }

    private fun isValidUserIdLength(userId: String): Boolean = userId.length in 1..255

    private fun isValidDeviceIdLength(deviceId: String): Boolean = deviceId.length in 1..255

    private fun isValidCampaignIdLength(campaignId: String): Boolean = campaignId.length in 1..1024

    private fun isValidEventNameLength(eventName: String): Boolean = eventName.length in 1..255

    /** Normalizes the SDK header, rejecting invalid lengths and line breaks in the original value. */
    private fun parseSdkVersion(value: String): String? {
        val header = value.trimJsWhitespace()
        return header.takeIf { it.length in 1..64 && '\r' !in value && '\n' !in value }
    }
}
