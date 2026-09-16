@file:OptIn(io.ktor.util.InternalAPI::class)
package tech.notifly.kmp.core.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.await
import kotlinx.coroutines.CancellationException
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import kotlin.js.Promise

internal actual fun createHttpClient(): HttpClient = HttpClient(BrowserFetchEngine { url, init ->
    js("globalThis").fetch(url, init).unsafeCast<Promise<dynamic>>()
}) {
    configureHttpClient()
}

internal class BrowserFetchEngine(private val fetch: (String, dynamic) -> Promise<dynamic>) : HttpClientEngineBase("notifly-fetch") {
    override val config = HttpClientEngineConfig()
    /**
     * Fetches a complete response without credentials, caching, or redirects.
     *
     * Cancellation remains attached through body reading, not just header receipt. The controller
     * is aborted on every exit, including cancellation or rejection of either awaited promise.
     * Native JavaScript errors are normalized at this boundary without exposing URL or body details.
     */
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val context = callContext()
        val requestTime = GMTDate()
        val controller = js("new AbortController()")
        val init = js("({})")
        init.method = data.method.value
        init.credentials = "omit"
        init.cache = "no-store"
        init.redirect = "error"
        init.signal = controller.signal
        val headers = js("({})")
        data.headers.forEach { name, values -> headers[name] = values.joinToString(",") }
        data.body.headers.forEach { name, values -> headers[name] = values.joinToString(",") }
        data.body.contentType?.let { headers[HttpHeaders.ContentType] = it.toString() }
        init.headers = headers
        when (val content = data.body) {
            is OutgoingContent.ByteArrayContent -> init.body = content.bytes().unsafeCast<Int8Array>()
            is OutgoingContent.NoContent -> Unit
            else -> error("Unsupported HTTP request content")
        }
        try {
            val raw = fetch(data.url.toString(), init).await()
            val status = (raw.status as Number).toInt()
            check(status in 100..599) { "Browser response has no HTTP status" }
            val responseHeaders = Headers.build {
                raw.headers.forEach { value: String, name: String -> append(name, value) }
            }
            val buffer = raw.arrayBuffer().unsafeCast<Promise<ArrayBuffer>>().await()
            return HttpResponseData(
                HttpStatusCode.fromValue(status), requestTime, responseHeaders, HttpProtocolVersion.HTTP_1_1,
                ByteReadChannel(Int8Array(buffer).unsafeCast<ByteArray>()), context,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw IllegalStateException("Browser HTTP transport failed")
        } finally {
            controller.abort()
        }
    }
}
