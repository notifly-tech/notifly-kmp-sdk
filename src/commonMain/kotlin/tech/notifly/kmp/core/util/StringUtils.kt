package tech.notifly.kmp.core.util

/**
 * Trims ECMAScript WhiteSpace and LineTerminator characters consistently across platforms.
 *
 * Kotlin's default trimming differs for the byte order mark and some control characters.
 */
internal fun String.trimJsWhitespace(): String = trim { it.isJsWhitespace() }

/** Checks for empty or ECMAScript-whitespace-only content without changing the original string. */
internal fun String.isJsBlank(): Boolean = all { it.isJsWhitespace() }

private fun Char.isJsWhitespace(): Boolean =
    this in '\u0009'..'\u000D' || this == '\u0020' || this == '\u00A0' || this == '\u1680' ||
        this in '\u2000'..'\u200A' || this == '\u2028' || this == '\u2029' || this == '\u202F' ||
        this == '\u205F' || this == '\u3000' || this == '\uFEFF'
