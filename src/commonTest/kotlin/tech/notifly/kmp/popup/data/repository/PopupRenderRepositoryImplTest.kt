package tech.notifly.kmp.popup.data.repository

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import tech.notifly.kmp.popup.domain.model.*

class PopupRenderRepositoryImplTest {
    private val input = ValidatedPopupRenderRequest("0123456789abcdef0123456789abcdef", "notifly/js/2.21.0", "campaign", "user", "device", " open ", null)

    private suspend fun execute(request: ValidatedPopupRenderRequest = input, base: String = "https://render.example/", handler: MockRequestHandler): PopupRenderResult {
        val client = HttpClient(MockEngine(handler)) { expectSuccess = false; followRedirects = false }
        try { return PopupRenderRepositoryImpl(base) { client }.render(request) } finally { client.close() }
    }

    @Test fun sendsExactWireContractAndPreservesOriginalHtmlAndNumericLiterals() = runTest {
        val html = "  <!doctype html><html>한글</html>\n"
        val params = "{\"nested\":{\"a\":[true,false,null,\"한글\",9007199254740993,1.2300e+40]}}"
        assertEquals(PopupRenderResult.Rendered(html), execute(input.copy(campaignId = "UJ~|~journey~|~node~|~session", eventParamsJson = params)) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/projects/0123456789abcdef0123456789abcdef/users/user/popup-pages/UJ~%7C~journey~%7C~node~%7C~session", request.url.encodedPath)
            assertEquals("notifly/js/2.21.0", request.headers["X-Notifly-SDK-Version"])
            assertEquals("text/html", request.headers[HttpHeaders.Accept])
            assertNull(request.headers[HttpHeaders.Authorization])
            assertNull(request.headers[HttpHeaders.Cookie])
            val content = request.body as OutgoingContent.ByteArrayContent
            assertEquals(ContentType.Application.Json, content.contentType?.withoutParameters())
            assertEquals("{\"deviceId\":\"device\",\"eventName\":\" open \",\"eventParams\":$params}", content.bytes().decodeToString())
            respond(html, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "Text/Html; charset=UTF-8"))
        })
    }

    @Test fun encodesEachOriginalIdAsOneSegment() = runTest {
        for ((id, encoded) in listOf(
            "campaign~|~variant" to "campaign~%7C~variant",
            "a/b%한😀" to "a%2Fb%25%ED%95%9C%F0%9F%98%80",
            "already%2Fencoded" to "already%252Fencoded",
            "a.b" to "a.b",
            "..." to "...",
            "a/.." to "a%2F..",
            "%2E" to "%252E",
        )) {
            assertEquals(PopupRenderResult.Skipped, execute(input.copy(campaignId = id, notiflyUserId = id)) {
                assertEquals("/projects/0123456789abcdef0123456789abcdef/users/$encoded/popup-pages/$encoded", it.url.encodedPath)
                respond("", HttpStatusCode.NoContent)
            })
        }
    }

    @Test fun classifiesStatusesWithoutParsingErrorBodiesOrRetrying() = runTest {
        for ((status, code) in listOf(400 to "invalid_request", 404 to "popup_not_found", 408 to "request_timeout", 413 to "payload_too_large", 422 to "invalid_popup_template", 500 to "internal_server_error", 504 to "popup_render_timeout", 201 to "unexpected_http_status", 302 to "unexpected_http_status", 429 to "unexpected_http_status", 503 to "unexpected_http_status")) {
            var calls = 0
            assertEquals(PopupRenderResult.Failed(code, status), execute { calls++; respond("not json", HttpStatusCode.fromValue(status)) })
            assertEquals(1, calls)
        }
        assertEquals(PopupRenderResult.Skipped, execute { respond("", HttpStatusCode.NoContent) })
    }

    @Test fun rejectsNonHtmlAndBlank200() = runTest {
        for ((body, type) in listOf("<html/>" to "application/json", "" to "text/html", " \uFEFF\u00A0\n" to "text/html", "ok" to "text/html-extra")) {
            assertEquals(PopupRenderResult.Failed("invalid_response", 200), execute { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, type)) })
        }
        assertEquals(PopupRenderResult.Failed("invalid_response", 200), execute { respond("html", HttpStatusCode.OK) })
    }

    @Test fun rejectsMalformedBaseUrlsBeforeCreatingClient() = runTest {
        for (base in listOf("", "render.example", "http://render.example", "https:///", "https://user@render.example", "https://:pass@render.example", "https://render.example?", "https://render.example#", "https://render.example/v1", "https://render.example:444", "https://render.example//", " https://render.example", "https://render.example\\evil")) {
            val repository = PopupRenderRepositoryImpl(base) { error("client must stay lazy: $base") }
            assertEquals(PopupRenderResult.Failed("invalid_configuration"), repository.render(input), base)
        }
    }

    @Test fun acceptsInjectedHttpsOriginsAndNormalizesSlash() = runTest {
        for (base in listOf("https://render.example", "https://render.example/", "https://render.example:443/", "https://other.example/")) {
            assertEquals(PopupRenderResult.Skipped, execute(base = base) { assertFalse(it.url.encodedPath.startsWith("//")); respond("", HttpStatusCode.NoContent) })
        }
    }

    @Test fun rejectsNonObjectAndMalformedJsonBeforeCreatingClient() = runTest {
        val repository = PopupRenderRepositoryImpl("https://render.example") { error("must not create client") }
        for (json in listOf("", " ", "null", "[]", "1", "true", "\"str\"", "{", "{\"x\":NaN}", "{\"x\":invalid}", "{\"x\":01}", "{\"x\":+1}", "{\"x\":1.}", "{\"x\":.1}", "{\"x\":Infinity}", "{\"x\":1e}", "{x:1}", "{\"x\":\"raw\nnewline\"}", "{} trailing")) {
            assertEquals(PopupRenderResult.Failed("invalid_request"), repository.render(input.copy(eventParamsJson = json)), json)
        }
    }

    @Test fun nullParamsBecomesEmptyObject() = runTest {
        assertEquals(PopupRenderResult.Skipped, execute {
            assertEquals("{\"deviceId\":\"device\",\"eventName\":\" open \",\"eventParams\":{}}", (it.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            respond("", HttpStatusCode.NoContent)
        })
    }

    @Test fun preservesValidDuplicateKeysLargeNumbersAndQuotedTokenText() = runTest {
        val params = "{\"x\":0,\"x\":{\"big\":9007199254740993,\"exp\":1.2300e+40,\"text\":\"NaN invalid 01\"}}"
        assertEquals(PopupRenderResult.Skipped, execute(input.copy(eventParamsJson = params)) {
            assertEquals(
                "{\"deviceId\":\"device\",\"eventName\":\" open \",\"eventParams\":{\"x\":{\"big\":9007199254740993,\"exp\":1.2300e+40,\"text\":\"NaN invalid 01\"}}}",
                (it.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
            )
            respond("", HttpStatusCode.NoContent)
        })
    }

    @Test fun sendsLargeJsonWithoutLocalSizeLimit() = runTest {
        for (value in listOf("a".repeat(300000), "한".repeat(100000))) {
            val params = "{\"x\":\"$value\"}"
            assertEquals(PopupRenderResult.Skipped, execute(input.copy(eventParamsJson = params)) {
                assertEquals(
                    "{\"deviceId\":\"device\",\"eventName\":\" open \",\"eventParams\":$params}",
                    (it.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                )
                respond("", HttpStatusCode.NoContent)
            })
        }
    }

    @Test fun serializedBodyCanExceedFormerSizeLimit() = runTest {
        // This wire prefix and closing suffix occupy 63 ASCII bytes.
        val base = input.copy(eventName = "open", eventParamsJson = "{\"x\":\"" + "a".repeat(262082) + "\"}")
        assertEquals(PopupRenderResult.Skipped, execute(base) {
            assertEquals(262145, (it.body as OutgoingContent.ByteArrayContent).bytes().size)
            respond("", HttpStatusCode.NoContent)
        })
    }

    @Test fun rejectsLargeMalformedJsonBeforeCreatingClient() = runTest {
        val repository = PopupRenderRepositoryImpl("https://render.example") { error("must not create client") }
        val params = "{\"x\":" + " ".repeat(300000) + "NaN}"
        assertEquals(PopupRenderResult.Failed("invalid_request"), repository.render(input.copy(eventParamsJson = params)))
    }

    @Test fun transportFailuresHaveNoStatusIncludingBodyFailure() = runTest {
        assertEquals(PopupRenderResult.Failed("network_error"), execute { throw IllegalStateException("offline") })
        assertEquals(PopupRenderResult.Failed("client_timeout"), execute { throw HttpRequestTimeoutException(it) })
        val broken = ByteChannel(autoFlush = true).also { it.close(IllegalStateException("body disconnected")) }
        assertEquals(PopupRenderResult.Failed("network_error"), execute { respond(broken, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html")) })
    }
}
