@file:JvmName("JvmHttpClient")
package tech.notifly.kmp.core.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.RequestBody
import okio.BufferedSink

internal actual fun createHttpClient(timeoutMillis: Long): HttpClient = HttpClient(OkHttp) {
    configureHttpClient(timeoutMillis)
    engine {
        config {
            retryOnConnectionFailure(false)
            followRedirects(false)
            followSslRedirects(false)
            cookieJar(CookieJar.NO_COOKIES)
            cache(null)
            authenticator(Authenticator.NONE)
            proxyAuthenticator(Authenticator.NONE)
            // OkHttp may otherwise replay a byte-array POST for 503 + Retry-After: 0,
            // even when connection retries are disabled.
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
