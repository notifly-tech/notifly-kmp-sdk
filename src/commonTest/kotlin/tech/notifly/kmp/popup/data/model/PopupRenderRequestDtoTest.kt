package tech.notifly.kmp.popup.data.model

import kotlinx.serialization.json.Json
import tech.notifly.kmp.popup.domain.model.ValidatedPopupRenderRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PopupRenderRequestDtoTest {
    private fun encode(params: Map<String, Any?>?): PopupRequestEncoding =
        PopupRenderRequestDto.encode(
            ValidatedPopupRenderRequest("project", "sdk", "campaign", "user", "device", "purchase", params),
        )

    @Test
    fun encode_nestedCollections_preservesJsonValues() {
        val params =
            mapOf(
                "items" to listOf(mapOf("id" to "P1", "quantity" to 2)),
                "tags" to listOf("sale", "new"),
                "enabled" to true,
                "nullable" to null,
                "price" to 1.25,
                "big" to 9007199254740993L,
                "text" to "한글\n\"quoted\"",
            )

        val body = assertIs<PopupRequestEncoding.Body>(encode(params)).json

        assertEquals(
            Json.parseToJsonElement(
                """{"deviceId":"device","eventName":"purchase","eventParams":{"items":[{"id":"P1","quantity":2}],"tags":["sale","new"],"enabled":true,"nullable":null,"price":1.25,"big":9007199254740993,"text":"한글\n\"quoted\""}}""",
            ),
            Json.parseToJsonElement(body),
        )
    }

    @Test
    fun encode_nullOrEmptyMap_usesEmptyObject() {
        for (params in listOf(null, emptyMap<String, Any?>())) {
            val body = assertIs<PopupRequestEncoding.Body>(encode(params)).json

            assertEquals("""{"deviceId":"device","eventName":"purchase","eventParams":{}}""", body)
        }
    }

    @Test
    fun encode_nonJsonValues_returnsInvalidRequest() {
        for (value in listOf(Any(), 'x', setOf("a"), mapOf(1 to "value"))) {
            val result = encode(mapOf("nested" to listOf(value)))

            assertEquals(PopupRequestEncoding.Invalid("invalid_request"), result)
        }
    }

    @Test
    fun encode_nonFiniteNumbers_returnsInvalidRequest() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Float.NaN)) {
            val result = encode(mapOf("value" to value))

            assertEquals(PopupRequestEncoding.Invalid("invalid_request"), result)
        }
    }

    @Test
    fun encode_circularCollections_returnsInvalidRequest() {
        val map = mutableMapOf<String, Any?>()
        map["self"] = map
        val list = mutableListOf<Any?>()
        list.add(list)

        assertEquals(PopupRequestEncoding.Invalid("invalid_request"), encode(map))
        assertEquals(PopupRequestEncoding.Invalid("invalid_request"), encode(mapOf("list" to list)))
    }

    @Test
    fun encode_sharedNonCircularCollection_encodesEveryOccurrence() {
        val shared = listOf(1, null)

        val body = assertIs<PopupRequestEncoding.Body>(encode(mapOf("a" to shared, "b" to shared))).json

        assertEquals(
            """{"deviceId":"device","eventName":"purchase","eventParams":{"a":[1,null],"b":[1,null]}}""",
            body,
        )
    }
}
