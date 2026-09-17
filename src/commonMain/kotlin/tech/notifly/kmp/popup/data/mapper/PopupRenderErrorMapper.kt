package tech.notifly.kmp.popup.data.mapper

/** Maps popup HTTP statuses to error codes after the repository handles successful responses. */
internal fun popupRenderErrorCode(status: Int): String =
    when (status) {
        400 -> "invalid_request"
        404 -> "popup_not_found"
        408 -> "request_timeout"
        413 -> "payload_too_large"
        422 -> "invalid_popup_template"
        500 -> "internal_server_error"
        504 -> "popup_render_timeout"
        else -> "unexpected_http_status"
    }
