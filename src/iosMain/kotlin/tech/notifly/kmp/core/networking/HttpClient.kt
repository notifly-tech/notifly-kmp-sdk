@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package tech.notifly.kmp.core.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.*

internal actual fun createHttpClient(timeoutMillis: Long): HttpClient = HttpClient(Darwin) {
    configureHttpClient(timeoutMillis)
    engine {
        configureSession {
            HTTPCookieStorage = null
            URLCredentialStorage = null
            URLCache = null
            HTTPShouldSetCookies = false
            requestCachePolicy = NSURLRequestReloadIgnoringLocalCacheData
        }
        configureRequest {
            setHTTPShouldHandleCookies(false)
            setCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
        }
        // Ktor's Darwin delegate rejects NSURLSession redirects; the common client also
        // disables Ktor-level redirects. Default TLS verification remains enabled.
    }
}
