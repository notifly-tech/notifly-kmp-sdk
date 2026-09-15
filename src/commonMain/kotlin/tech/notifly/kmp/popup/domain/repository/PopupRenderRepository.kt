package tech.notifly.kmp.popup.domain.repository

import tech.notifly.kmp.popup.domain.model.PopupRenderResult
import tech.notifly.kmp.popup.domain.model.ValidatedPopupRenderRequest

internal fun interface PopupRenderRepository {
    suspend fun render(request: ValidatedPopupRenderRequest): PopupRenderResult
}
