@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package tech.notifly.kmp.core.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.setHTTPShouldHandleCookies

/**
 * Creates an isolated Darwin session without cookie storage, saved credentials, or response caching.
 *
 * Ktor's Darwin delegate rejects NSURLSession redirects, while shared configuration disables
 * Ktor-level redirects. Default TLS verification remains enabled.
 */
internal actual fun createHttpClient(): HttpClient =
    HttpClient(Darwin) {
        configureHttpClient()
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
        }
    }
