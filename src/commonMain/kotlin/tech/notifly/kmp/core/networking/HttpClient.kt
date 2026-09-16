package tech.notifly.kmp.core.networking

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/** Creates a platform transport owned by the caller, who must close it when no longer needed. */
internal expect fun createHttpClient(): HttpClient

/** Leaves HTTP status classification to the repository and prevents Ktor-level redirects. */
internal fun HttpClientConfig<*>.configureHttpClient() {
    expectSuccess = false
    followRedirects = false
}
