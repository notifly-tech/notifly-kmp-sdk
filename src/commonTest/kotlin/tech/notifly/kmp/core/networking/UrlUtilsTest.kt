package tech.notifly.kmp.core.networking

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlUtilsTest {
    @Test
    fun encodePathSegment_unreservedCharacters_preservesOriginalValue() {
        assertEquals("abcXYZ019-._~", encodePathSegment("abcXYZ019-._~"))
        assertEquals("", encodePathSegment(""))
    }

    @Test
    fun encodePathSegment_reservedCharacters_encodesAsSingleSegment() {
        assertEquals("%2F%20%3F%23%25%2B%3A%40%5C%3D%26", encodePathSegment("/ ?#%+:@\\=&"))
        assertEquals("%252F", encodePathSegment("%2F"))
    }

    @Test
    fun encodePathSegment_unicode_encodesUtf8Bytes() {
        assertEquals("%ED%95%9C%EA%B8%80%F0%9F%98%80", encodePathSegment("한글😀"))
    }
}
