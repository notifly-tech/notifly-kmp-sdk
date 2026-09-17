package tech.notifly.kmp.popup

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import tech.notifly.kmp.popup.model.PopupRenderInput
import tech.notifly.kmp.popup.model.PopupRenderOutput
import tech.notifly.kmp.popup.model.PopupRendererConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PopupRenderInputJsTest {
    private fun input(
        params: dynamic,
        mode: String? = "ssr",
    ): PopupRenderInput = createPopupRenderInput(mode, "campaign", "user", "device", "purchase", params)

    private suspend fun TestScope.render(
        input: PopupRenderInput,
        expectedParams: String? = null,
    ): PopupRenderOutput {
        var created = 0
        var requests = 0
        var client: HttpClient? = null
        val renderer =
            createPopupRenderer(
                PopupRendererConfig("0123456789abcdef0123456789abcdef", "https://render.example", "sdk"),
                {
                    created++
                    HttpClient(
                        MockEngine { request ->
                            requests++
                            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                            assertEquals(
                                Json.parseToJsonElement(checkNotNull(expectedParams)),
                                Json.parseToJsonElement(body).jsonObject["eventParams"],
                            )
                            respond("", HttpStatusCode.NoContent)
                        },
                    ).also { client = it }
                },
                StandardTestDispatcher(testScheduler),
            )
        try {
            val completion = CompletableDeferred<PopupRenderOutput>()
            renderer.render(input) { completion.complete(it) }
            val output = completion.await()

            assertEquals(if (expectedParams == null) 0 else 1, created)
            assertEquals(if (expectedParams == null) 0 else 1, requests)
            return output
        } finally {
            client?.close()
        }
    }

    @Test
    fun createPopupRenderInput_nestedObjectsAndArrays_sendsJsonValues() =
        runTest {
            val params =
                js("({items: [{id: 'P1', quantity: 2}], tags: ['sale', 'new'], enabled: true, nil: null, price: 1.25})")

            val output =
                render(
                    input(params),
                    """{"items":[{"id":"P1","quantity":2}],"tags":["sale","new"],"enabled":true,"nil":null,"price":1.25}""",
                )

            assertEquals("skipped", output.outcome)
        }

    @Test
    fun createPopupRenderInput_nullOrUndefined_sendsEmptyObject() =
        runTest {
            for (params in listOf(null, js("undefined"))) {
                val output = render(input(params), "{}")

                assertEquals("skipped", output.outcome)
            }
        }

    @Test
    fun createPopupRenderInput_nonObjectRoot_returnsInvalidRequest() =
        runTest {
            for (params in listOf(
                js("[]"),
                "text",
                1,
                true,
                js("new Map()"),
                js("new Date()"),
                js("new (function Event() { this.id = 'P1'; })()"),
            )) {
                val output = render(input(params))

                assertEquals("failed", output.outcome)
                assertEquals("invalid_request", output.errorCode)
                assertNull(output.httpStatus)
            }
        }

    @Test
    fun createPopupRenderInput_unsupportedNestedValue_returnsInvalidRequest() =
        runTest {
            for (value in listOf(
                js("undefined"),
                js("function () {}"),
                js("Symbol('x')"),
                js("BigInt(1)"),
                js("NaN"),
                js("Infinity"),
                js("new Date()"),
                js("new Uint8Array([1])"),
            )) {
                val params = js("({nested: []})")
                params.nested.push(value)

                val output = render(input(params))

                assertEquals("invalid_request", output.errorCode)
            }
        }

    @Test
    fun createPopupRenderInput_circularObjectsAndArrays_returnsInvalidRequest() =
        runTest {
            val objectCycle = js("({})")
            objectCycle.self = objectCycle
            val arrayCycle = js("[]")
            arrayCycle.push(arrayCycle)
            val nestedCycle = js("({})")
            nestedCycle.items = arrayCycle

            for (params in listOf(objectCycle, nestedCycle)) {
                val output = render(input(params))

                assertEquals("invalid_request", output.errorCode)
            }
        }

    @Test
    fun createPopupRenderInput_sharedObject_sendsEachOccurrence() =
        runTest {
            val shared = js("({id: 'P1'})")
            val params = js("({})")
            params.first = shared
            params.second = shared

            val output = render(input(params), """{"first":{"id":"P1"},"second":{"id":"P1"}}""")

            assertEquals("skipped", output.outcome)
        }

    @Test
    fun createPopupRenderInput_staticMode_doesNotReadParameters() =
        runTest {
            val params =
                js(
                    "Object.defineProperty({}, 'value', {enumerable: true, get: function () { throw new Error('must not be read'); }})",
                )

            for (mode in listOf(null, "static", "unknown")) {
                val output = render(input(params, mode))

                assertEquals("static", output.outcome)
                assertNull(output.errorCode)
            }
        }

    @Test
    fun createPopupRenderInput_throwingGetter_returnsInvalidRequest() =
        runTest {
            val params =
                js(
                    "Object.defineProperty({}, 'value', {enumerable: true, get: function () { throw new TypeError('unreadable'); }})",
                )

            val output = render(input(params))

            assertEquals("invalid_request", output.errorCode)
        }

    @Test
    fun createPopupRenderInput_getterThrowsNonErrorValue_returnsInvalidRequest() =
        runTest {
            val params = js("Object.defineProperty({}, 'value', {enumerable: true, get: function () { throw 17; }})")

            val output = render(input(params))

            assertEquals("invalid_request", output.errorCode)
        }

    @Test
    fun createPopupRenderInput_nullPrototypeAndSpecialKeys_preservesOwnFields() =
        runTest {
            val params = js("Object.create(null)")
            params["__proto__"] = "value"
            params["constructor"] = "name"

            val output = render(input(params), """{"__proto__":"value","constructor":"name"}""")

            assertEquals("skipped", output.outcome)
        }
}
