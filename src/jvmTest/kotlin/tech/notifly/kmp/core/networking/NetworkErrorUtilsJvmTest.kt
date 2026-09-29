package tech.notifly.kmp.core.networking

import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkErrorUtilsJvmTest {
    @Test
    fun networkErrorCode_javaSocketTimeout_returnsClientTimeout() {
        val error = SocketTimeoutException("Read timed out")

        assertEquals("client_timeout", networkErrorCode(error))
    }

    @Test
    fun networkErrorCode_connectionRefused_returnsNetworkError() {
        val error = ConnectException("Connection refused")

        assertEquals("network_error", networkErrorCode(error))
    }
}
