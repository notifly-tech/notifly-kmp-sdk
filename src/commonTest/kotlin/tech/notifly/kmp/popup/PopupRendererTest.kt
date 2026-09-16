@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tech.notifly.kmp.popup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import tech.notifly.kmp.popup.domain.model.PopupRenderResult
import tech.notifly.kmp.popup.domain.model.ValidatedPopupRenderRequest
import tech.notifly.kmp.popup.domain.repository.PopupRenderRepository
import tech.notifly.kmp.popup.domain.usecase.RenderPopupUseCase
import tech.notifly.kmp.popup.model.PopupRenderInput
import tech.notifly.kmp.popup.model.PopupRenderOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PopupRendererTest {
    private val input = PopupRenderInput("ssr", "campaign", "user", "device", "open", emptyMap())

    private fun TestScope.renderer(
        block: suspend (ValidatedPopupRenderRequest) -> PopupRenderResult = { PopupRenderResult.Rendered("html") },
    ) = PopupRenderer(
        RenderPopupUseCase("0123456789abcdef0123456789abcdef", "sdk", PopupRenderRepository(block)),
        StandardTestDispatcher(testScheduler),
    )

    @Test
    fun immediateSuccessIsAsynchronousAndFinal() =
        runTest {
            val outcomes = mutableListOf<PopupRenderOutput>()
            val renderer = renderer()
            val task = renderer.render(input) { outcomes.add(it) }
            assertTrue(outcomes.isEmpty())
            runCurrent()
            task.cancel()
            runCurrent()
            assertEquals(1, outcomes.size)
            assertEquals("rendered", outcomes.single().outcome)
            assertEquals("html", outcomes.single().html)
            assertEquals(200, outcomes.single().httpStatus)
            assertNull(outcomes.single().errorCode)
        }

    @Test
    fun immediateCancelWinsAndCallbackSurvivesCancellation() =
        runTest {
            val outcomes = mutableListOf<PopupRenderOutput>()
            val renderer = renderer { error("cancelled before execution") }
            val task = renderer.render(input) { outcomes.add(it) }
            task.cancel()
            task.cancel()
            assertTrue(outcomes.isEmpty())
            runCurrent()
            assertEquals(listOf("cancelled"), outcomes.map { it.outcome })
            assertNull(outcomes.single().html)
            assertNull(outcomes.single().httpStatus)
            assertNull(outcomes.single().errorCode)
        }

    @Test
    fun cancelDiscardsUncooperativeLateHtmlAndDoesNotCancelOtherRequest() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val outcomes = mutableListOf<String>()
            var calls = 0
            val renderer =
                renderer {
                    calls++
                    if (calls == 1) withContext(NonCancellable) { gate.await() }
                    PopupRenderResult.Rendered("html")
                }
            val task = renderer.render(input) { outcomes.add(it.outcome) }
            runCurrent()
            task.cancel()
            renderer.render(input) { outcomes.add(it.outcome) }
            runCurrent()
            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf("cancelled", "rendered"), outcomes)
        }

    @Test
    fun render_afterCancellation_acceptsStaticAndSsrRequests() =
        runTest {
            val outcomes = mutableListOf<PopupRenderOutput>()
            val renderer = renderer()

            val task = renderer.render(input) { outcomes.add(it) }
            task.cancel()
            assertTrue(outcomes.isEmpty())
            renderer.render(PopupRenderInput(null, null, null, null, null, null)) { outcomes.add(it) }
            renderer.render(input) { outcomes.add(it) }
            runCurrent()

            assertEquals(listOf("cancelled", "static", "rendered"), outcomes.map { it.outcome })
            assertEquals("html", outcomes.last().html)
        }

    @Test
    fun pendingRequestSurvivesTwentySecondsAndCanStillBeCancelled() =
        runTest {
            var requestCancelled = false
            val outcomes = mutableListOf<PopupRenderOutput>()
            val renderer =
                renderer {
                    try {
                        awaitCancellation()
                    } finally {
                        requestCancelled = true
                    }
                }
            val task = renderer.render(input) { outcomes.add(it) }
            runCurrent()
            try {
                advanceTimeBy(60000)
                runCurrent()
                assertTrue(outcomes.isEmpty())
                assertFalse(requestCancelled)
            } finally {
                task.cancel()
                runCurrent()
            }
            assertTrue(requestCancelled)
            assertEquals(1, outcomes.size)
            assertEquals("cancelled", outcomes.single().outcome)
            assertNull(outcomes.single().errorCode)
            assertNull(outcomes.single().httpStatus)
        }

    @Test
    fun returnsHtmlAfterTwentySeconds() =
        runTest {
            val outcomes = mutableListOf<PopupRenderOutput>()
            val renderer =
                renderer {
                    delay(30000)
                    PopupRenderResult.Rendered("late html")
                }
            val task = renderer.render(input) { outcomes.add(it) }
            try {
                advanceTimeBy(30000)
                runCurrent()
                assertEquals(1, outcomes.size)
                assertEquals("rendered", outcomes.single().outcome)
                assertEquals("late html", outcomes.single().html)
                assertEquals(200, outcomes.single().httpStatus)
                assertNull(outcomes.single().errorCode)
            } finally {
                task.cancel()
            }
        }

    @Test
    fun completedServerErrorsKeepStatusAfterCancel() =
        runTest {
            for ((code, status) in listOf(
                "request_timeout" to 408,
                "payload_too_large" to 413,
                "popup_render_timeout" to 504,
            )) {
                val outcomes = mutableListOf<PopupRenderOutput>()
                val renderer = renderer { PopupRenderResult.Failed(code, status) }
                val task = renderer.render(input) { outcomes.add(it) }
                runCurrent()
                advanceTimeBy(20001)
                task.cancel()
                runCurrent()
                assertEquals(code, outcomes.single().errorCode)
                assertEquals(status, outcomes.single().httpStatus)
            }
        }

    @Test
    fun staticInvalidSkippedAndInternalFailureCompleteOnce() =
        runTest {
            val outcomes = mutableListOf<PopupRenderOutput>()
            val renderer = renderer { PopupRenderResult.Skipped }
            renderer.render(
                PopupRenderInput("static", null, null, null, null, mapOf("bad" to Any())),
            ) { outcomes.add(it) }
            renderer.render(PopupRenderInput("ssr", null, null, null, null, null)) { outcomes.add(it) }
            renderer.render(input) { outcomes.add(it) }
            assertTrue(outcomes.isEmpty())
            runCurrent()
            assertEquals(listOf("static", "failed", "skipped"), outcomes.map { it.outcome })
            assertEquals("invalid_request", outcomes[1].errorCode)
            assertEquals(204, outcomes[2].httpStatus)
            val broken = renderer { error("recoverable internal failure") }
            broken.render(input) { outcomes.add(it) }
            runCurrent()
            assertEquals("internal_error", outcomes.last().errorCode)
        }

    @Test
    fun callbackExceptionDoesNotBecomeSecondCompletionOrBreakOtherRequests() =
        runTest {
            var calls = 0
            val renderer = renderer()
            renderer.render(input) {
                calls++
                error("caller failure")
            }
            renderer.render(input) { calls++ }
            runCurrent()
            assertEquals(2, calls)
        }

    @Test
    fun render_callbackStartsAnotherRequest_deliversBothResults() =
        runTest {
            val renderer = renderer()
            val outcomes = mutableListOf<String>()
            renderer.render(input) {
                outcomes.add(it.outcome)
                renderer.render(input) { output -> outcomes.add(output.outcome) }
            }
            runCurrent()
            assertEquals(listOf("rendered", "rendered"), outcomes)
        }
}
