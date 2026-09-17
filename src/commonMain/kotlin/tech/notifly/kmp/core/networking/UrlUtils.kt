package tech.notifly.kmp.core.networking

import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * Parses an HTTPS origin on port 443, allowing only an optional root slash.
 *
 * Returns null for malformed URLs, credentials, query strings, fragments, or non-root paths.
 * This validates URL structure without restricting the hostname.
 */
internal fun parseHttpsOrigin(value: String): Url? {
    if (!value.startsWith("https://", ignoreCase = true) ||
        value.any { it <= ' ' || it == '\\' || it == '@' || it == '?' || it == '#' }
    ) {
        return null
    }
    if (value.substringAfter("://").substringBefore('/').isEmpty()) return null
    return try {
        Url(value).takeIf {
            it.protocol == URLProtocol.HTTPS && it.host.isNotEmpty() && it.port == 443 &&
                it.user == null && it.password == null && (it.encodedPath.isEmpty() || it.encodedPath == "/")
        }
    } catch (error: Exception) {
        null
    }
}

/** Encodes an original value as one UTF-8 path segment without interpreting existing percent escapes. */
internal fun encodePathSegment(value: String): String =
    buildString {
        for (byte in value.encodeToByteArray()) {
            val code = byte.toInt() and 255
            val char = code.toChar()
            if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in "-._~") {
                append(char)
            } else {
                append('%')
                append("0123456789ABCDEF"[code ushr 4])
                append("0123456789ABCDEF"[code and 15])
            }
        }
    }
