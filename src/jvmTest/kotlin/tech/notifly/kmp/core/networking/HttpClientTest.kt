package tech.notifly.kmp.core.networking

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.pluginOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import java.util.concurrent.TimeUnit
import kotlin.test.*

class HttpClientTest {
    @Test fun doesNotInstallKmpTimeoutPolicy() {
        val client = createHttpClient()
        try { assertNull(client.pluginOrNull(HttpTimeout)) } finally { client.close() }
    }

    @Test fun doesNotFollowRedirectsOrReplayServiceUnavailable() = runBlocking {
        for (status in listOf(302, 307, 503)) {
            val server = MockWebServer()
            server.start()
            server.enqueue(MockResponse().setResponseCode(status).addHeader("Location", server.url("/elsewhere")).addHeader("Retry-After", "0"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("unexpected replay"))
            val client = createHttpClient()
            try {
                val response = client.post(server.url("/render").toString()) { setBody("private body") }
                assertEquals(status, response.status.value)
                assertEquals(1, server.requestCount)
            } finally { client.close(); server.shutdown() }
        }
    }

    @Test fun disconnectDoesNotReplayRequest() = runBlocking {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setBody("unexpected replay"))
        val client = createHttpClient()
        try {
            var failed = false
            try { client.post(server.url("/render").toString()) { setBody("private body") }.bodyAsText() } catch (error: Exception) { failed = true }
            assertTrue(failed)
            assertEquals(1, server.requestCount)
        } finally { client.close(); server.shutdown() }
    }

    @Test fun responseCookiesAreNotAttachedToLaterCalls() = runBlocking {
        val server = MockWebServer()
        server.start()
        repeat(2) { server.enqueue(MockResponse().setBody("html").addHeader("Set-Cookie", "secret=value; Path=/")) }
        val client = createHttpClient()
        try {
            repeat(2) { client.post(server.url("/render").toString()) { setBody("body") }.bodyAsText() }
            repeat(2) { assertNull(server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("Cookie")) }
        } finally { client.close(); server.shutdown() }
    }

    @Test fun readsDelayedBodyWithPlatformTimeoutDefaults() = runBlocking {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setBody("late body").setBodyDelay(500, TimeUnit.MILLISECONDS))
        val client = createHttpClient()
        try {
            val body = client.preparePost(server.url("/render").toString()) { setBody("body") }.execute { it.bodyAsText() }
            assertEquals("late body", body)
            assertEquals(1, server.requestCount)
        } finally { client.close(); server.shutdown() }
    }
}
