package tech.notifly.kmp.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsValueUtilsTest {
    @Test
    fun jsObjectToMap_nestedObjectsAndArrays_convertsNativeCollections() {
        val value = js("({items: [{id: 'P1'}], enabled: true, nullable: null, price: 1.25})")

        val actual = jsObjectToMap(value)

        assertEquals(
            mapOf("items" to listOf(mapOf("id" to "P1")), "enabled" to true, "nullable" to null, "price" to 1.25),
            actual,
        )
    }

    @Test
    fun jsObjectToMap_nullPrototypeObject_convertsOwnProperties() {
        val value = js("Object.assign(Object.create(null), {id: 'P1'})")

        assertEquals(mapOf("id" to "P1"), jsObjectToMap(value))
    }

    @Test
    fun jsObjectToMap_nonPlainObjectRoot_throwsInvalidArgument() {
        for (value in listOf(null, js("undefined"), js("[]"), "text", js("new Date()"))) {
            assertFailsWith<IllegalArgumentException> { jsObjectToMap(value) }
        }
    }

    @Test
    fun jsObjectToMap_unsupportedNestedValues_throwsInvalidArgument() {
        for (value in listOf(js("({x: undefined})"), js("({x: function() {}})"), js("({x: new Date()})"))) {
            assertFailsWith<IllegalArgumentException> { jsObjectToMap(value) }
        }
    }

    @Test
    fun jsObjectToMap_circularObject_throwsInvalidArgument() {
        val value = js("({})")
        value.self = value

        assertFailsWith<IllegalArgumentException> { jsObjectToMap(value) }
    }

    @Test
    fun jsObjectToMap_sharedNonCircularObject_convertsEachOccurrence() {
        val value = js("(function() { var shared = {id: 'P1'}; return {a: shared, b: shared}; })()")

        assertEquals(mapOf("a" to mapOf("id" to "P1"), "b" to mapOf("id" to "P1")), jsObjectToMap(value))
    }
}
