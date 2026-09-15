package tech.notifly.kmp.popup.domain.model

internal sealed class PopupRenderResult {
    object Static : PopupRenderResult()
    data class Rendered(val html: String) : PopupRenderResult()
    object Skipped : PopupRenderResult()
    data class Failed(val errorCode: String, val httpStatus: Int? = null) : PopupRenderResult()
    object Cancelled : PopupRenderResult()
}
