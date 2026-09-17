package tech.notifly.kmp.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringUtilsTest {
    @Test
    fun trimJsWhitespace_ecmaWhitespace_removesOnlyLeadingAndTrailingCharacters() {
        val whitespace =
            "\u0009\u000A\u000B\u000C\u000D \u00A0\u1680" +
                "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A" +
                "\u2028\u2029\u202F\u205F\u3000\uFEFF"

        val actual = (whitespace + "a \uFEFF b" + whitespace).trimJsWhitespace()

        assertEquals("a \uFEFF b", actual)
    }

    @Test
    fun trimJsWhitespace_otherControlCharacters_preservesOriginalValue() {
        for (value in listOf("\u0000", "\u001C", "\u0085", "\u180E", "\u200B")) {
            assertEquals(value + "text" + value, (value + "text" + value).trimJsWhitespace())
        }
    }

    @Test
    fun isJsBlank_emptyOrWhitespaceOnly_returnsTrue() {
        for (value in listOf("", " \t\r\n", "\uFEFF\u00A0", "\u2000\u2028\u3000")) {
            assertTrue(value.isJsBlank())
        }
    }

    @Test
    fun isJsBlank_nonWhitespaceContent_returnsFalse() {
        for (value in listOf(" text ", "\uFEFF<html></html>\u00A0", "\u001C", "\u0085", "\u200B")) {
            assertFalse(value.isJsBlank())
        }
    }
}
