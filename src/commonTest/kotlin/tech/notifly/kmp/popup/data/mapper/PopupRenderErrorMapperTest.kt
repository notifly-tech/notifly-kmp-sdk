package tech.notifly.kmp.popup.data.mapper

import kotlin.test.Test
import kotlin.test.assertEquals

class PopupRenderErrorMapperTest {
    @Test
    fun popupRenderErrorCode_knownStatus_returnsMatchingCode() {
        val cases =
            listOf(
                400 to "invalid_request",
                404 to "popup_not_found",
                408 to "request_timeout",
                413 to "payload_too_large",
                422 to "invalid_popup_template",
                500 to "internal_server_error",
                504 to "popup_render_timeout",
            )

        for ((status, expected) in cases) {
            assertEquals(expected, popupRenderErrorCode(status), "HTTP $status")
        }
    }

    @Test
    fun popupRenderErrorCode_unknownStatus_returnsUnexpectedHttpStatus() {
        for (status in listOf(201, 302, 401, 429, 503)) {
            assertEquals("unexpected_http_status", popupRenderErrorCode(status), "HTTP $status")
        }
    }
}
