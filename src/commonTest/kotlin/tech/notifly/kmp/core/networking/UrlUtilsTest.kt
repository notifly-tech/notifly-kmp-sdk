package tech.notifly.kmp.core.networking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UrlUtilsTest {
    @Test
    fun parseHttpsOrigin_validHttpsOrigins_returnsParsedUrl() {
        for (value in listOf(
            "https://render.example",
            "https://render.example/",
            "https://render.example:443/",
            "HTTPS://render.example",
        )) {
            val origin = assertNotNull(parseHttpsOrigin(value), value)

            assertEquals("render.example", origin.host, value)
            assertEquals(443, origin.port, value)
        }
    }

    @Test
    fun parseHttpsOrigin_nonOriginComponents_returnsNull() {
        for (value in listOf(
            "https://user@render.example",
            "https://:pass@render.example",
            "https://render.example?",
            "https://render.example#",
            "https://render.example/v1",
            "https://render.example//",
            "https://render.example:444",
        )) {
            assertNull(parseHttpsOrigin(value), value)
        }
    }

    @Test
    fun parseHttpsOrigin_malformedUrls_returnsNull() {
        for (value in listOf(
            "",
            "render.example",
            "http://render.example",
            "https:///",
            "https:///render.example",
            "https://render.example:invalid",
            " https://render.example",
            "https://render.example\n",
            "https://render.example\\evil",
        )) {
            assertNull(parseHttpsOrigin(value), value)
        }
    }

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
