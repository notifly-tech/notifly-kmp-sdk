package tech.notifly.kmp.core.networking

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout

internal expect fun createHttpClient(timeoutMillis: Long): HttpClient

internal fun HttpClientConfig<*>.configureHttpClient(timeoutMillis: Long) {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) {
        requestTimeoutMillis = timeoutMillis
        connectTimeoutMillis = timeoutMillis
        socketTimeoutMillis = timeoutMillis
    }
}
