package tech.notifly.kmp.core.networking

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
