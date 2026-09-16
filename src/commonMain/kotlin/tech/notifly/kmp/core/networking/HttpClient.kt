package tech.notifly.kmp.core.networking

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

internal expect fun createHttpClient(): HttpClient

internal fun HttpClientConfig<*>.configureHttpClient() {
    expectSuccess = false
    followRedirects = false
}
