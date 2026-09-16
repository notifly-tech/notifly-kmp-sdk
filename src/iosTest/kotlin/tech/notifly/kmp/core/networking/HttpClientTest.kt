@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package tech.notifly.kmp.core.networking

import io.ktor.client.engine.darwin.DarwinClientEngineConfig
import platform.Foundation.*
import kotlin.test.*

class HttpClientTest {
    @Test fun nativeSessionAndRequestDisableSharedCookiesCredentialsAndCache() {
        val client = createHttpClient()
        try {
            val config = client.engine.config as DarwinClientEngineConfig
            val session = NSURLSessionConfiguration.defaultSessionConfiguration()
            config.sessionConfig(session)
            assertNull(session.HTTPCookieStorage)
            assertNull(session.URLCredentialStorage)
            assertNull(session.URLCache)
            assertFalse(session.HTTPShouldSetCookies)
            assertEquals(NSURLRequestReloadIgnoringLocalCacheData, session.requestCachePolicy)
            val request = NSMutableURLRequest.requestWithURL(NSURL(string = "https://render.example"))
            config.requestConfig(request)
            assertFalse(request.HTTPShouldHandleCookies)
            assertEquals(NSURLRequestReloadIgnoringLocalCacheData, request.cachePolicy)
        } finally { client.close() }
    }
}
