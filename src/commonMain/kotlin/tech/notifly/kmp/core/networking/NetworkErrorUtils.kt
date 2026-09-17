package tech.notifly.kmp.core.networking

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException

/**
 * Classifies transport exceptions without exposing engine-specific details.
 *
 * Rethrows cancellation unchanged so callers preserve coroutine cancellation.
 */
internal fun networkErrorCode(error: Exception): String =
    when (error) {
        is CancellationException -> throw error

        is HttpRequestTimeoutException,
        is ConnectTimeoutException,
        is SocketTimeoutException,
        -> "client_timeout"

        else -> "network_error"
    }
