@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tech.notifly.kmp.popup

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PopupFactoryTest {
    @Test
    fun clientIsLazyReusedAndClosedWithRenderer() =
        runTest {
            var created = 0
            var requests = 0
            var client: HttpClient? = null
            val renderer =
                createPopupRenderer(
                    PopupRendererConfig(
                        "0123456789abcdef0123456789abcdef",
                        "https://render.example",
                        "sdk",
                    ),
                    {
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
                        ).also {
                            client =
                                it
                        }
                    },
                    Dispatchers.Default,
                )
            val outputs = mutableListOf<PopupRenderOutput>()
            assertEquals(0, created)
            outputs.add(renderer.awaitOutput(PopupRenderInput(null, null, null, null, null, "bad")))
            outputs.add(renderer.awaitOutput(PopupRenderInput("ssr", null, null, null, null, null)))
            outputs.add(renderer.awaitOutput(PopupRenderInput("ssr", "campaign", "user", "device", "open", "[]")))
            assertEquals(0, created)
            val input = PopupRenderInput("ssr", "campaign", "user", "device", "open", "{}")
            outputs.add(renderer.awaitOutput(input))
            outputs.add(renderer.awaitOutput(input))
            assertEquals(1, created)
            assertEquals(2, requests)
            assertEquals(setOf("html-1", "html-2"), outputs.takeLast(2).map { it.html }.toSet())
            renderer.close()
            assertFalse(client!!.coroutineContext[Job]!!.isActive)
        }

    @Test
    fun invalidBaseUrlAndCloseNeverInitializeClient() =
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
            renderer.render(PopupRenderInput("ssr", "campaign", "user", "device", "open", "{}")) { output = it }
            runCurrent()
            assertEquals("invalid_configuration", output?.errorCode)
            renderer.close()
            renderer.close()
        }

    @Test
    fun malformedValuesHiddenByDuplicateKeysNeverCreateClientOrSendHttp() =
        runTest {
            var created = 0
            var requests = 0
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
                        )
                    },
                    Dispatchers.Default,
                )
            try {
                for (json in listOf(
                    "{\"x\":NaN,\"x\":1}",
                    "{\"x\":01,\"x\":1}",
                    "{\"x\":{\"bad\":NaN},\"x\":{}}",
                )) {
                    val output =
                        renderer.awaitOutput(
                            PopupRenderInput("ssr", "campaign", "user", "device", "open", json),
                        )
                    assertEquals("failed", output.outcome, json)
                    assertEquals("invalid_request", output.errorCode, json)
                    assertNull(output.httpStatus)
                }
                assertEquals(0, created)
                assertEquals(0, requests)
            } finally {
                renderer.close()
            }
        }

    @Test
    fun dotOnlyPathIdsNeverCreateClientOrSendHttp() =
        runTest {
            var created = 0
            var requests = 0
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
                        )
                    },
                    Dispatchers.Default,
                )
            try {
                for (dot in listOf(".", "..", "\uFEFF .\u00A0", " .. ")) {
                    for ((campaign, user) in listOf(dot to "user", "campaign" to dot)) {
                        val output =
                            renderer.awaitOutput(
                                PopupRenderInput("ssr", campaign, user, "device", "open", "{}"),
                            )
                        assertEquals("failed", output.outcome, "$campaign / $user")
                        assertEquals("invalid_request", output.errorCode)
                        assertNull(output.httpStatus)
                    }
                }
                assertEquals(0, created)
                assertEquals(0, requests)
            } finally {
                renderer.close()
            }
        }
}

private suspend fun PopupRenderer.awaitOutput(input: PopupRenderInput): PopupRenderOutput =
    suspendCoroutine { continuation -> render(input) { continuation.resume(it) } }
