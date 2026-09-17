package tech.notifly.kmp.core.networking

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class NetworkErrorUtilsTest {
    @Test
    fun networkErrorCode_timeout_returnsClientTimeout() {
        val errors =
            listOf(
                HttpRequestTimeoutException(HttpRequestBuilder()),
                ConnectTimeoutException("Connection timed out"),
                SocketTimeoutException("Socket timed out"),
            )

        for (error in errors) {
            assertEquals("client_timeout", networkErrorCode(error), error.toString())
        }
    }

    @Test
    fun networkErrorCode_otherException_returnsNetworkError() {
        val error = IllegalStateException("Offline")

        assertEquals("network_error", networkErrorCode(error))
    }

    @Test
    fun networkErrorCode_cancellation_rethrowsOriginalException() {
        val cancellation = CancellationException("Request cancelled")

        val thrown = assertFailsWith<CancellationException> { networkErrorCode(cancellation) }

        assertSame(cancellation, thrown)
    }
}
