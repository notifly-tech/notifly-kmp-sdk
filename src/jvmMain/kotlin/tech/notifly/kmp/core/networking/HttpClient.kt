@file:JvmName("JvmHttpClient")
package tech.notifly.kmp.core.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.RequestBody
import okio.BufferedSink

/**
 * Creates an OkHttp transport with retries, redirects, cookies, caching, and authentication disabled.
 *
 * Request bodies are marked one-shot because disabling connection retries alone does not prevent
 * OkHttp from replaying a byte-array POST after HTTP 503 with `Retry-After: 0`.
 */
internal actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    configureHttpClient()
    engine {
        config {
            retryOnConnectionFailure(false)
            followRedirects(false)
            followSslRedirects(false)
            cookieJar(CookieJar.NO_COOKIES)
            cache(null)
            authenticator(Authenticator.NONE)
            proxyAuthenticator(Authenticator.NONE)
            addInterceptor { chain ->
                val request = chain.request()
                val body = request.body
                val once = if (body == null) request else request.newBuilder().method(request.method, object : RequestBody() {
                    override fun contentType() = body.contentType()
                    override fun contentLength() = body.contentLength()
                    override fun isOneShot() = true
                    override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
                }).build()
                chain.proceed(once)
            }
        }
    }
}
