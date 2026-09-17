@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tech.notifly.kmp.popup

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tech.notifly.kmp.popup.model.PopupRenderInput
import tech.notifly.kmp.popup.model.PopupRenderOutput
import tech.notifly.kmp.popup.model.PopupRendererConfig
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PopupFactoryTest {
    @Test
    fun render_repeatedRequests_reusesLazyBorrowedClient() =
        runTest {
            var created = 0
            var requests = 0
            val transport =
                lazy {
                    created++
                    HttpClient(
                        MockEngine {
                            requests++
                            respond(
                                "html-$requests",
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "text/html"),
                            )
                        },
                    )
                }
            val renderer =
                createPopupRenderer(
                    PopupRendererConfig(
                        "0123456789abcdef0123456789abcdef",
                        "https://render.example",
                        "sdk",
                    ),
                    { transport.value },
                    StandardTestDispatcher(testScheduler),
                )
            try {
                val outputs = mutableListOf<PopupRenderOutput>()
                assertEquals(0, created)
                outputs.add(renderer.awaitOutput(PopupRenderInput(null, null, null, null, null, mapOf("bad" to Any()))))
                outputs.add(renderer.awaitOutput(PopupRenderInput("ssr", null, null, null, null, null)))
                outputs.add(
                    renderer.awaitOutput(
                        PopupRenderInput("ssr", "campaign", "user", "device", "open", mapOf("bad" to Any())),
                    ),
                )
                assertEquals(0, created)

                val input = PopupRenderInput("ssr", "campaign", "user", "device", "open", emptyMap())
                outputs.add(renderer.awaitOutput(input))
                outputs.add(renderer.awaitOutput(input))

                assertEquals(1, created)
                assertEquals(2, requests)
                assertEquals(listOf("html-1", "html-2"), outputs.takeLast(2).map { it.html })
                assertTrue(transport.value.coroutineContext[Job]!!.isActive)
            } finally {
                if (transport.isInitialized()) transport.value.close()
            }
        }

    @Test
    fun render_invalidBaseUrl_doesNotInitializeClient() =
        runTest {
            val renderer =
                createPopupRenderer(
                    PopupRendererConfig(
                        "0123456789abcdef0123456789abcdef",
                        "http://render.example",
                        "sdk",
                    ),
                    {
                        error("must stay lazy")
                    },
                    StandardTestDispatcher(testScheduler),
                )
            var output: PopupRenderOutput? = null
            renderer.render(PopupRenderInput("ssr", "campaign", "user", "device", "open", emptyMap())) { output = it }
            runCurrent()
            assertEquals("invalid_configuration", output?.errorCode)
        }

    @Test
    fun render_unsupportedNestedValues_returnsInvalidRequestWithoutCreatingClient() =
        runTest {
            var created = 0
            var requests = 0
            var client: HttpClient? = null
            val renderer =
                createPopupRenderer(
                    PopupRendererConfig("0123456789abcdef0123456789abcdef", "https://render.example", "sdk"),
                    {
                        created++
                        HttpClient(
                            MockEngine {
                                requests++
                                respond("", HttpStatusCode.NoContent)
                            },
                        ).also { client = it }
                    },
                    StandardTestDispatcher(testScheduler),
                )
            try {
                for (params in listOf(
                    mapOf("x" to Double.NaN),
                    mapOf("x" to listOf(Any())),
                    mapOf("x" to mapOf("bad" to Double.POSITIVE_INFINITY)),
                )) {
                    val output =
                        renderer.awaitOutput(
                            PopupRenderInput("ssr", "campaign", "user", "device", "open", params),
                        )
                    assertEquals("failed", output.outcome, params.toString())
                    assertEquals("invalid_request", output.errorCode, params.toString())
                    assertNull(output.httpStatus)
                }
                assertEquals(0, created)
                assertEquals(0, requests)
            } finally {
                client?.close()
            }
        }

    @Test
    fun dotOnlyPathIdsNeverCreateClientOrSendHttp() =
        runTest {
            var created = 0
            var requests = 0
            var client: HttpClient? = null
            val renderer =
                createPopupRenderer(
                    PopupRendererConfig("0123456789abcdef0123456789abcdef", "https://render.example", "sdk"),
                    {
                        created++
                        HttpClient(
                            MockEngine {
                                requests++
                                respond("", HttpStatusCode.NoContent)
                            },
                        ).also { client = it }
                    },
                    StandardTestDispatcher(testScheduler),
                )
            try {
                for (dot in listOf(".", "..", "\uFEFF .\u00A0", " .. ")) {
                    for ((campaign, user) in listOf(dot to "user", "campaign" to dot)) {
                        val output =
                            renderer.awaitOutput(
                                PopupRenderInput("ssr", campaign, user, "device", "open", emptyMap()),
                            )
                        assertEquals("failed", output.outcome, "$campaign / $user")
                        assertEquals("invalid_request", output.errorCode)
                        assertNull(output.httpStatus)
                    }
                }
                assertEquals(0, created)
                assertEquals(0, requests)
            } finally {
                client?.close()
            }
        }

    @Test
    fun render_sharedClient_keepsRendererConfigurationsSeparate() =
        runTest {
            val requests = mutableListOf<Pair<String, String>>()
            val client =
                HttpClient(
                    MockEngine { request ->
                        requests.add(request.url.toString() to checkNotNull(request.headers["X-Notifly-SDK-Version"]))
                        respond("html", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
                    },
                )
            try {
                val first =
                    createPopupRenderer(
                        PopupRendererConfig("0123456789abcdef0123456789abcdef", "https://first.example", "sdk-first"),
                        { client },
                        StandardTestDispatcher(testScheduler),
                    )
                val second =
                    createPopupRenderer(
                        PopupRendererConfig("abcdef0123456789abcdef0123456789", "https://second.example", "sdk-second"),
                        { client },
                        StandardTestDispatcher(testScheduler),
                    )

                val firstOutput =
                    first.awaitOutput(PopupRenderInput("ssr", "campaign-a", "user-a", "device", "open", null))
                val secondOutput =
                    second.awaitOutput(PopupRenderInput("ssr", "campaign-b", "user-b", "device", "open", null))

                assertEquals("rendered", firstOutput.outcome)
                assertEquals("rendered", secondOutput.outcome)
                assertEquals(
                    listOf(
                        (
                            "https://first.example/projects/0123456789abcdef0123456789abcdef" +
                                "/users/user-a/popup-pages/campaign-a"
                        ) to
                            "sdk-first",
                        (
                            "https://second.example/projects/abcdef0123456789abcdef0123456789" +
                                "/users/user-b/popup-pages/campaign-b"
                        ) to
                            "sdk-second",
                    ),
                    requests,
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun cancel_sharedClient_cancelsOnlyItsRequestAndAllowsReuse() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val stopped = CompletableDeferred<Unit>()
            val otherStarted = CompletableDeferred<Unit>()
            val releaseOther = CompletableDeferred<Unit>()
            val client =
                HttpClient(
                    MockEngine { request ->
                        if (request.url.encodedPath.endsWith("/pending")) {
                            started.complete(Unit)
                            try {
                                awaitCancellation()
                            } finally {
                                stopped.complete(Unit)
                            }
                        }
                        if (request.url.encodedPath.endsWith("/other")) {
                            otherStarted.complete(Unit)
                            releaseOther.await()
                        }
                        respond("html", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
                    },
                )
            var task: PopupRenderTask? = null
            var otherTask: PopupRenderTask? = null
            try {
                val config = PopupRendererConfig("0123456789abcdef0123456789abcdef", "https://render.example", "sdk")
                val first = createPopupRenderer(config, { client }, StandardTestDispatcher(testScheduler))
                val second = createPopupRenderer(config, { client }, StandardTestDispatcher(testScheduler))
                val cancelled = CompletableDeferred<PopupRenderOutput>()
                task =
                    first.render(PopupRenderInput("ssr", "pending", "user", "device", "open", null)) {
                        cancelled.complete(it)
                    }
                started.await()
                val other = CompletableDeferred<PopupRenderOutput>()
                otherTask =
                    second.render(PopupRenderInput("ssr", "other", "user", "device", "open", null)) {
                        other.complete(it)
                    }
                otherStarted.await()

                task.cancel()
                val cancelledOutput = cancelled.await()
                stopped.await()
                releaseOther.complete(Unit)
                val otherOutput = other.await()
                val reusedOutput = first.awaitOutput(PopupRenderInput("ssr", "next", "user", "device", "open", null))

                assertEquals("cancelled", cancelledOutput.outcome)
                assertEquals("rendered", otherOutput.outcome)
                assertEquals("rendered", reusedOutput.outcome)
                assertTrue(client.coroutineContext[Job]!!.isActive)
            } finally {
                task?.cancel()
                otherTask?.cancel()
                client.close()
            }
        }
}

private suspend fun PopupRenderer.awaitOutput(input: PopupRenderInput): PopupRenderOutput =
    suspendCoroutine { continuation -> render(input) { continuation.resume(it) } }
