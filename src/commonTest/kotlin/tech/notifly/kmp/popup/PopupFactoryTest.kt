@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package tech.notifly.kmp.popup

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.test.*
import kotlin.test.*
import tech.notifly.kmp.popup.model.*

class PopupFactoryTest {
    @Test fun clientIsLazyReusedAndClosedWithRenderer() = runTest {
        var created = 0
        var requests = 0
        var client: HttpClient? = null
        val renderer = createPopupRenderer(PopupRendererConfig("0123456789abcdef0123456789abcdef", "https://render.example", "sdk"), {
            created++
            HttpClient(MockEngine { requests++; respond("html-${requests}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html")) }).also { client = it }
        }, Dispatchers.Default) { 0L }
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

    @Test fun invalidBaseUrlAndCloseNeverInitializeClient() = runTest {
        val renderer = createPopupRenderer(PopupRendererConfig("0123456789abcdef0123456789abcdef", "http://render.example", "sdk"), { error("must stay lazy") }, StandardTestDispatcher(testScheduler)) { testScheduler.currentTime }
        var output: PopupRenderOutput? = null
        renderer.render(PopupRenderInput("ssr", "campaign", "user", "device", "open", "{}")) { output = it }
        runCurrent()
        assertEquals("invalid_configuration", output?.errorCode)
        renderer.close()
        renderer.close()
    }
}

private suspend fun PopupRenderer.awaitOutput(input: PopupRenderInput): PopupRenderOutput =
    suspendCoroutine { continuation -> render(input) { continuation.resume(it) } }
