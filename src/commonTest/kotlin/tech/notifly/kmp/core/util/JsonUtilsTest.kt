package tech.notifly.kmp.core.util

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonUtilsTest {
    @Test
    fun toJsonElement_nestedCollections_preservesSupportedValues() {
        val value =
            mapOf(
                "items" to listOf(mapOf("id" to "P1", "count" to 2)),
                "enabled" to true,
                "nullable" to null,
                "price" to 1.25,
                "text" to " spaced \n\"text\" ",
            )

        val actual = value.toJsonElement()

        assertEquals(
            Json.parseToJsonElement(
                """{"items":[{"id":"P1","count":2}],"enabled":true,"nullable":null,"price":1.25,"text":" spaced \n\"text\" "}""",
            ),
            actual,
        )
    }

    @Test
    fun toJsonElement_largeInteger_preservesExactDigits() {
        assertEquals("9007199254740993", 9007199254740993L.toJsonElement().toString())
    }

    @Test
    fun toJsonElement_unsupportedValues_throwsInvalidArgument() {
        for (value in listOf(Any(), Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, mapOf(1 to "x"))) {
            assertFailsWith<IllegalArgumentException> { mapOf("nested" to listOf(value)).toJsonElement() }
        }
    }

    @Test
    fun toJsonElement_circularCollections_throwsInvalidArgument() {
        val map = mutableMapOf<String, Any?>()
        val list = mutableListOf<Any?>()
        map["items"] = list
        list.add(map)

        assertFailsWith<IllegalArgumentException> { map.toJsonElement() }
    }

    @Test
    fun toJsonElement_sharedNonCircularCollection_encodesEachOccurrence() {
        val shared = listOf("value", null)

        val actual = mapOf("a" to shared, "b" to shared).toJsonElement()

        assertEquals(Json.parseToJsonElement("""{"a":["value",null],"b":["value",null]}"""), actual)
    }
}
