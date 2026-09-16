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
            // Keep cancellation attached until the entire body is available, not just headers.
            val buffer = raw.arrayBuffer().unsafeCast<Promise<ArrayBuffer>>().await()
            return HttpResponseData(
                HttpStatusCode.fromValue(status), requestTime, responseHeaders, HttpProtocolVersion.HTTP_1_1,
                ByteReadChannel(Int8Array(buffer).unsafeCast<ByteArray>()), context,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            // Browser fetch rejects native JS Error objects, which are not Kotlin Exceptions.
            // Normalize at this foreign-runtime boundary and do not expose URL/body details.
            throw IllegalStateException("Browser HTTP transport failed")
        } finally {
            // Also runs when either Promise await is cancelled or rejects.
            controller.abort()
        }
    }
}
