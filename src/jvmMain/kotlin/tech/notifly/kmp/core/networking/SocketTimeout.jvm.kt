package tech.notifly.kmp.core.networking

import java.net.SocketTimeoutException

/** Uses the JDK superclass shared by Ktor 2 exceptions and Ktor 3 aliases. */
internal actual fun isSocketTimeout(error: Exception): Boolean = error is SocketTimeoutException
