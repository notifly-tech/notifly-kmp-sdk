package tech.notifly.kmp.core.networking

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import java.util.concurrent.TimeUnit
import kotlin.test.*

class HttpClientTest {
    @Test fun doesNotFollowRedirectsOrReplayServiceUnavailable() = runBlocking {
        for (status in listOf(302, 307, 503)) {
            val server = MockWebServer()
            server.start()
            server.enqueue(MockResponse().setResponseCode(status).addHeader("Location", server.url("/elsewhere")).addHeader("Retry-After", "0"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("unexpected replay"))
            val client = createHttpClient(2000)
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
        val client = createHttpClient(2000)
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
        val client = createHttpClient(2000)
        try {
            repeat(2) { client.post(server.url("/render").toString()) { setBody("body") }.bodyAsText() }
            repeat(2) { assertNull(server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("Cookie")) }
        } finally { client.close(); server.shutdown() }
    }

    @Test fun timeoutCoversResponseBodyAfterHeaders() = runBlocking {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setBody("late body").setBodyDelay(500, TimeUnit.MILLISECONDS))
        val client = createHttpClient(100)
        try {
            var failure: Exception? = null
            try {
                client.preparePost(server.url("/render").toString()) { setBody("body") }.execute { it.bodyAsText() }
            } catch (error: Exception) { failure = error }
            assertTrue(failure is HttpRequestTimeoutException || failure is SocketTimeoutException, "Expected body timeout, got ${failure?.javaClass?.simpleName}")
            assertEquals(1, server.requestCount)
        } finally { client.close(); server.shutdown() }
    }
}
