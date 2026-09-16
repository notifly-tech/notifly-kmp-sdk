package tech.notifly.kmp.popup.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.test.runTest
import tech.notifly.kmp.popup.domain.model.PopupRenderResult
import tech.notifly.kmp.popup.domain.model.ValidatedPopupRenderRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PopupRenderRepositoryImplTest {
    private val input =
        ValidatedPopupRenderRequest(
            "0123456789abcdef0123456789abcdef",
            "notifly/js/2.21.0",
            "campaign",
            "user",
            "device",
            " open ",
            null,
        )

    private suspend fun execute(
        request: ValidatedPopupRenderRequest = input,
        base: String = "https://render.example/",
        handler: MockRequestHandler,
    ): PopupRenderResult {
        val client =
            HttpClient(MockEngine(handler)) {
                expectSuccess = false
                followRedirects = false
            }
        try {
            return PopupRenderRepositoryImpl(base) { client }.render(request)
        } finally {
            client.close()
        }
    }

    @Test
    fun render_nestedEventParams_preservesWireContractAndOriginalHtml() =
        runTest {
            val html = "  <!doctype html><html>한글</html>\n"
            val params = mapOf("nested" to mapOf("a" to listOf(true, false, null, "한글", 9007199254740993L, 1.25)))
            assertEquals(
                PopupRenderResult.Rendered(html),
                execute(input.copy(campaignId = "UJ~|~journey~|~node~|~session", eventParams = params)) { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        "/projects/0123456789abcdef0123456789abcdef/users/user/popup-pages/UJ~%7C~journey~%7C~node~%7C~session",
                        request.url.encodedPath,
                    )
                    assertEquals("notifly/js/2.21.0", request.headers["X-Notifly-SDK-Version"])
                    assertEquals("text/html", request.headers[HttpHeaders.Accept])
                    assertNull(request.headers[HttpHeaders.Authorization])
                    assertNull(request.headers[HttpHeaders.Cookie])
                    val content = request.body as OutgoingContent.ByteArrayContent
                    assertEquals(ContentType.Application.Json, content.contentType?.withoutParameters())
                    assertEquals(
                        """{"deviceId":"device","eventName":" open ","eventParams":{"nested":{"a":[true,false,null,"한글",9007199254740993,1.25]}}}""",
                        content.bytes().decodeToString(),
                    )
                    respond(html, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "Text/Html; charset=UTF-8"))
                },
            )
        }

    @Test
    fun encodesEachOriginalIdAsOneSegment() =
        runTest {
            for ((id, encoded) in listOf(
                "campaign~|~variant" to "campaign~%7C~variant",
                "a/b%한😀" to "a%2Fb%25%ED%95%9C%F0%9F%98%80",
                "already%2Fencoded" to "already%252Fencoded",
                "a.b" to "a.b",
                "..." to "...",
                "a/.." to "a%2F..",
                "%2E" to "%252E",
            )) {
                assertEquals(
                    PopupRenderResult.Skipped,
                    execute(input.copy(campaignId = id, notiflyUserId = id)) {
                        assertEquals(
                            "/projects/0123456789abcdef0123456789abcdef/users/$encoded/popup-pages/$encoded",
                            it.url.encodedPath,
                        )
                        respond("", HttpStatusCode.NoContent)
                    },
                )
            }
        }

    @Test
    fun classifiesStatusesWithoutParsingErrorBodiesOrRetrying() =
        runTest {
            for ((status, code) in listOf(
                400 to "invalid_request",
                404 to "popup_not_found",
                408 to "request_timeout",
                413 to "payload_too_large",
                422 to "invalid_popup_template",
                500 to "internal_server_error",
                504 to "popup_render_timeout",
                201 to "unexpected_http_status",
                302 to "unexpected_http_status",
                429 to "unexpected_http_status",
                503 to "unexpected_http_status",
            )) {
                var calls = 0
                assertEquals(
                    PopupRenderResult.Failed(code, status),
                    execute {
                        calls++
                        respond("not json", HttpStatusCode.fromValue(status))
                    },
                )
                assertEquals(1, calls)
            }
            assertEquals(PopupRenderResult.Skipped, execute { respond("", HttpStatusCode.NoContent) })
        }

    @Test
    fun rejectsNonHtmlAndBlank200() =
        runTest {
            for ((body, type) in listOf(
                "<html/>" to "application/json",
                "" to "text/html",
                " \uFEFF\u00A0\n" to "text/html",
                "ok" to "text/html-extra",
            )) {
                assertEquals(
                    PopupRenderResult.Failed("invalid_response", 200),
                    execute {
                        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, type))
                    },
                )
            }
            assertEquals(
                PopupRenderResult.Failed("invalid_response", 200),
                execute { respond("html", HttpStatusCode.OK) },
            )
        }

    @Test
    fun rejectsMalformedBaseUrlsBeforeCreatingClient() =
        runTest {
            for (base in listOf(
                "",
                "render.example",
                "http://render.example",
                "https:///",
                "https://user@render.example",
                "https://:pass@render.example",
                "https://render.example?",
                "https://render.example#",
                "https://render.example/v1",
                "https://render.example:444",
                "https://render.example//",
                " https://render.example",
                "https://render.example\\evil",
            )) {
                val repository = PopupRenderRepositoryImpl(base) { error("client must stay lazy: $base") }
                assertEquals(PopupRenderResult.Failed("invalid_configuration"), repository.render(input), base)
            }
        }

    @Test
    fun acceptsInjectedHttpsOriginsAndNormalizesSlash() =
        runTest {
            for (base in listOf(
                "https://render.example",
                "https://render.example/",
                "https://render.example:443/",
                "https://other.example/",
            )) {
                assertEquals(
                    PopupRenderResult.Skipped,
                    execute(base = base) {
                        assertFalse(it.url.encodedPath.startsWith("//"))
                        respond("", HttpStatusCode.NoContent)
                    },
                )
            }
        }

    @Test
    fun render_nonJsonValues_rejectsBeforeCreatingClient() =
        runTest {
            val repository = PopupRenderRepositoryImpl("https://render.example") { error("must not create client") }
            for (value in listOf(
                Any(),
                Double.NaN,
                Double.POSITIVE_INFINITY,
                mapOf(1 to "non-string key"),
            )) {
                assertEquals(
                    PopupRenderResult.Failed("invalid_request"),
                    repository.render(input.copy(eventParams = mapOf("value" to value))),
                    value.toString(),
                )
            }
        }

    @Test
    fun nullParamsBecomesEmptyObject() =
        runTest {
            assertEquals(
                PopupRenderResult.Skipped,
                execute {
                    assertEquals(
                        "{\"deviceId\":\"device\",\"eventName\":\" open \",\"eventParams\":{}}",
                        (it.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                    )
                    respond("", HttpStatusCode.NoContent)
                },
            )
        }

    @Test
    fun render_largeIntegerAndTokenLikeText_preservesValues() =
        runTest {
            val params = mapOf("x" to mapOf("big" to 9007199254740993L, "text" to "NaN invalid 01"))
            assertEquals(
                PopupRenderResult.Skipped,
                execute(input.copy(eventParams = params)) {
                    assertEquals(
                        "{\"deviceId\":\"device\",\"eventName\":\" open \",\"eventParams\":{\"x\":{\"big\":9007199254740993,\"text\":\"NaN invalid 01\"}}}",
                        (it.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                    )
                    respond("", HttpStatusCode.NoContent)
                },
            )
        }

    @Test
    fun sendsLargeJsonWithoutLocalSizeLimit() =
        runTest {
            for (value in listOf("a".repeat(300000), "한".repeat(100000))) {
                val params = mapOf("x" to value)
                assertEquals(
                    PopupRenderResult.Skipped,
                    execute(input.copy(eventParams = params)) {
                        assertEquals(
                            "{\"deviceId\":\"device\",\"eventName\":\" open \",\"eventParams\":{\"x\":\"$value\"}}",
                            (it.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                        )
                        respond("", HttpStatusCode.NoContent)
                    },
                )
            }
        }

    /** Uses 63 bytes of JSON overhead to exceed the former 256 KiB limit by exactly one byte. */
    @Test
    fun serializedBodyCanExceedFormerSizeLimit() =
        runTest {
            val base = input.copy(eventName = "open", eventParams = mapOf("x" to "a".repeat(262082)))
            assertEquals(
                PopupRenderResult.Skipped,
                execute(base) {
                    assertEquals(262145, (it.body as OutgoingContent.ByteArrayContent).bytes().size)
                    respond("", HttpStatusCode.NoContent)
                },
            )
        }

    @Test
    fun render_largeParamsWithNonFiniteNumber_rejectsBeforeCreatingClient() =
        runTest {
            val repository = PopupRenderRepositoryImpl("https://render.example") { error("must not create client") }
            val params = mapOf("text" to " ".repeat(300000), "value" to Double.NaN)
            assertEquals(
                PopupRenderResult.Failed("invalid_request"),
                repository.render(input.copy(eventParams = params)),
            )
        }

    @Test
    fun transportFailuresHaveNoStatusIncludingBodyFailure() =
        runTest {
            assertEquals(PopupRenderResult.Failed("network_error"), execute { throw IllegalStateException("offline") })
            assertEquals(PopupRenderResult.Failed("client_timeout"), execute { throw HttpRequestTimeoutException(it) })
            val broken = ByteChannel(autoFlush = true).also { it.close(IllegalStateException("body disconnected")) }
            assertEquals(
                PopupRenderResult.Failed("network_error"),
                execute {
                    respond(broken, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
                },
            )
        }
}
