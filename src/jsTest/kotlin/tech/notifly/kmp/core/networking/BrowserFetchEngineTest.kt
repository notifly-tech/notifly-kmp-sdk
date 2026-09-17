package tech.notifly.kmp.core.networking

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tech.notifly.kmp.popup.createPopupRenderer
import tech.notifly.kmp.popup.data.repository.PopupRenderRepositoryImpl
import tech.notifly.kmp.popup.domain.model.PopupRenderResult
import tech.notifly.kmp.popup.domain.model.ValidatedPopupRenderRequest
import tech.notifly.kmp.popup.model.PopupRenderInput
import tech.notifly.kmp.popup.model.PopupRendererConfig
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BrowserFetchEngineTest {
    private fun response(
        status: Int = 200,
        body: () -> Promise<dynamic> = { Promise.resolve(js("new Uint8Array([104, 105]).buffer")) },
    ): dynamic {
        val value = js("({})")
        value.status = status
        value.statusText = "OK"
        value.headers = js("new Headers({'Content-Type': 'text/html'})")
        value.arrayBuffer = body
        return value
    }

    @Test
    fun fetchGetsPrivacyOptionsAndEncodedBodyBeforeSending() =
        runTest {
            var calls = 0
            val client =
                HttpClient(
                    BrowserFetchEngine { url, init ->
                        calls++
                        assertEquals("https://render.example/request", url)
                        assertEquals("omit", init.credentials)
                        assertEquals("no-store", init.cache)
                        assertEquals("error", init.redirect)
                        assertEquals("POST", init.method)
                        assertNotNull(init.signal)
                        assertEquals("body", js("new TextDecoder().decode(init.body)") as String)
                        Promise.resolve(response())
                    },
                ) { configureHttpClient() }
            try {
                assertEquals("hi", client.post("https://render.example/request") { setBody("body") }.bodyAsText())
                assertEquals(1, calls)
            } finally {
                client.close()
            }
        }

    @Test
    fun cancellationAbortsWhileBodyPromiseIsStillPending() =
        runTest {
            val bodyStarted = CompletableDeferred<Unit>()
            var signal: dynamic = null
            val client =
                HttpClient(
                    BrowserFetchEngine { _, init ->
                        signal = init.signal
                        Promise.resolve(
                            response {
                                bodyStarted.complete(Unit)
                                Promise { _, _ -> }
                            },
                        )
                    },
                ) { configureHttpClient() }
            val job = launch { client.post("https://render.example").bodyAsText() }
            bodyStarted.await()
            job.cancelAndJoin()
            assertEquals(true, signal.aborted)
            client.close()
        }

    @Test
    fun cancellationAbortsBeforeResponseHeaders() =
        runTest {
            val started = CompletableDeferred<Unit>()
            var signal: dynamic = null
            val client =
                HttpClient(
                    BrowserFetchEngine { _, init ->
                        signal = init.signal
                        started.complete(Unit)
                        Promise { _, _ -> }
                    },
                ) { configureHttpClient() }
            val job = launch { client.post("https://render.example").bodyAsText() }
            started.await()
            job.cancelAndJoin()
            assertEquals(true, signal.aborted)
            client.close()
        }

    @Test
    fun rejectsOpaqueStatusZeroAndBodyFailures() =
        runTest {
            for (raw in listOf(response(0), response { Promise.reject(IllegalStateException("disconnected")) })) {
                val client = HttpClient(BrowserFetchEngine { _, _ -> Promise.resolve(raw) }) { configureHttpClient() }
                try {
                    var failed = false
                    try {
                        client.post("https://render.example").bodyAsText()
                    } catch (error: Exception) {
                        failed = true
                    }
                    assertTrue(failed)
                } finally {
                    client.close()
                }
            }
        }

    @Test
    fun nativeJavaScriptRejectionsBecomeNetworkErrorWithoutStatus() =
        runTest {
            val request =
                ValidatedPopupRenderRequest(
                    "0123456789abcdef0123456789abcdef",
                    "sdk",
                    "campaign",
                    "user",
                    "device",
                    "open",
                    emptyMap(),
                )
            for (duringBody in listOf(false, true)) {
                val client =
                    HttpClient(
                        BrowserFetchEngine { _, _ ->
                            if (duringBody) {
                                Promise.resolve(response { Promise.reject(js("new TypeError('body failed')")) })
                            } else {
                                Promise.reject(js("new TypeError('fetch failed')"))
                            }
                        },
                    ) { configureHttpClient() }
                try {
                    assertEquals(
                        PopupRenderResult.Failed(
                            "network_error",
                        ),
                        PopupRenderRepositoryImpl("https://render.example") {
                            client
                        }.render(request),
                    )
                } finally {
                    client.close()
                }
            }
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun nativeJavaScriptCallbackExceptionDoesNotEscapeOrBlockOtherCalls() =
        runTest {
            val renderer =
                createPopupRenderer(PopupRendererConfig("", "", ""), {
                    error("static needs no client")
                }, StandardTestDispatcher(testScheduler))
            val input = PopupRenderInput(null, null, null, null, null, null)
            var calls = 0
            renderer.render(input) {
                calls++
                js("throw new TypeError('callback failed')")
            }
            renderer.render(input) { calls++ }
            runCurrent()
            assertEquals(2, calls)
        }
}
