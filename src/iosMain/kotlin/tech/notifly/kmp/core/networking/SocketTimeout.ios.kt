package tech.notifly.kmp.core.networking

import io.ktor.client.network.sockets.SocketTimeoutException

internal actual fun isSocketTimeout(error: Exception): Boolean = error is SocketTimeoutException
