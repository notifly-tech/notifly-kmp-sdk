package tech.notifly.kmp.core.networking

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class BrowserHttpIntegrationTest {
    private val sameOrigin: String
        get() = window.location.origin

    private val crossOrigin: String
        get() {
            val alias = if (window.location.hostname == "localhost") "127.0.0.1" else "localhost"
            return "${window.location.protocol}//$alias:${window.location.port}"
        }

    @Test
    fun sameAndCrossOriginRequestsOmitCookiesAndUseOnlyAllowedPreflightHeaders() =
        runTest {
            val client = createHttpClient()
            document.cookie = "notifly_popup_fixture=synthetic; Path=/; SameSite=Lax"
            try {
                client.post("$sameOrigin/__notifly_popup_fixture/reset")
                val sameOriginResponse =
                    client.post("$sameOrigin/__notifly_popup_fixture/html") {
                        contentType(ContentType.Application.Json)
                        header("X-Notifly-SDK-Version", "browser-smoke")
                        setBody("{}")
                    }
                assertEquals(HttpStatusCode.OK, sameOriginResponse.status)
                assertEquals("<div>popup fixture</div>", sameOriginResponse.bodyAsText())
                assertEquals("absent", sameOriginResponse.headers["X-Observed-Cookie"])
                assertEquals("absent", sameOriginResponse.headers["X-Observed-Accept-Charset"])

                val crossOriginResponse =
                    client.post("$crossOrigin/__notifly_popup_fixture/html") {
                        contentType(ContentType.Application.Json)
                        header("X-Notifly-SDK-Version", "browser-smoke")
                        setBody("{}")
                    }
                assertEquals(HttpStatusCode.OK, crossOriginResponse.status)
                assertEquals("<div>popup fixture</div>", crossOriginResponse.bodyAsText())
                assertEquals("absent", crossOriginResponse.headers["X-Observed-Cookie"])
                assertEquals("1", crossOriginResponse.headers["X-Observed-Preflight-Count"])
                val preflightHeaders =
                    checkNotNull(
                        crossOriginResponse.headers["X-Observed-Preflight-Headers"],
                    ).lowercase()
                assertTrue("content-type" in preflightHeaders)
                assertTrue("x-notifly-sdk-version" in preflightHeaders)
                assertFalse("accept-charset" in preflightHeaders)
            } finally {
                document.cookie = "notifly_popup_fixture=; Max-Age=0; Path=/; SameSite=Lax"
                client.close()
            }
        }

    @Test
    fun emptyAndRedirectResponsesPreserveStatusAndNeverFollowRedirects() =
        runTest {
            val client = createHttpClient()
            try {
                client.post("$sameOrigin/__notifly_popup_fixture/reset")
                val empty = client.post("$sameOrigin/__notifly_popup_fixture/empty")
                assertEquals(HttpStatusCode.NoContent, empty.status)
                assertEquals("", empty.bodyAsText())

                var rejected = false
                try {
                    client.post("$sameOrigin/__notifly_popup_fixture/redirect").bodyAsText()
                } catch (error: IllegalStateException) {
                    rejected = true
                }
                assertTrue(rejected)
                val state = client.post("$sameOrigin/__notifly_popup_fixture/state")
                assertEquals("0", state.headers["X-Redirect-Hits"])
            } finally {
                client.close()
            }
        }

    @Test
    fun cancellationAbortsAfterHeadersWhileResponseBodyIsPending() =
        runTest {
            val client = createHttpClient()
            try {
                client.post("$sameOrigin/__notifly_popup_fixture/reset")
                val request =
                    launch {
                        client.post("$sameOrigin/__notifly_popup_fixture/slow").bodyAsText()
                    }
                awaitState(client, "X-Slow-Started", "1")
                request.cancelAndJoin()
                awaitState(client, "X-Slow-Aborted", "1")
            } finally {
                client.close()
            }
        }

    private suspend fun awaitState(
        client: io.ktor.client.HttpClient,
        header: String,
        expected: String,
    ) {
        repeat(100) {
            val state = client.post("$sameOrigin/__notifly_popup_fixture/state")
            if (state.headers[header] == expected) return
        }
        fail("fixture state $header did not become $expected")
    }
}
