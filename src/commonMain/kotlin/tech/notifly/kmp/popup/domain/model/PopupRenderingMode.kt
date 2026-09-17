package tech.notifly.kmp.popup.domain.model

internal enum class PopupRenderingMode {
    STATIC,
    SSR,
    ;

    companion object {
        fun from(value: String?): PopupRenderingMode = if (value == "ssr") SSR else STATIC
    }
}
