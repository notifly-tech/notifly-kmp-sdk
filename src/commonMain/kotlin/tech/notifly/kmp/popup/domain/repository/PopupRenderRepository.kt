package tech.notifly.kmp.popup.domain.repository

import tech.notifly.kmp.popup.domain.model.PopupRenderResult
import tech.notifly.kmp.popup.domain.model.ValidatedPopupRenderRequest

internal fun interface PopupRenderRepository {
    /**
     * Fetches rendered HTML or a skip result after validating the origin and event JSON.
     *
     * Validation and transport failures are reported as [PopupRenderResult.Failed]; coroutine
     * cancellation is propagated so the caller retains control of the request lifecycle.
     */
    suspend fun render(request: ValidatedPopupRenderRequest): PopupRenderResult
}
