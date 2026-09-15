@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package tech.notifly.kmp.popup

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import tech.notifly.kmp.popup.domain.model.*
import tech.notifly.kmp.popup.domain.repository.PopupRenderRepository
import tech.notifly.kmp.popup.domain.usecase.RenderPopupUseCase
import tech.notifly.kmp.popup.model.*

class PopupRendererTest {
    private val input = PopupRenderInput("ssr", "campaign", "user", "device", "open", "{}")
    private fun TestScope.renderer(
        close: () -> Unit = {},
        clock: () -> Long = { testScheduler.currentTime },
        block: suspend (ValidatedPopupRenderRequest) -> PopupRenderResult = { PopupRenderResult.Rendered("html") },
    ) = PopupRenderer(RenderPopupUseCase("0123456789abcdef0123456789abcdef", "sdk", PopupRenderRepository(block)), close, StandardTestDispatcher(testScheduler), clock)

    @Test fun immediateSuccessIsAsynchronousAndFinal() = runTest {
        val outcomes = mutableListOf<PopupRenderOutput>()
        val renderer = renderer()
        val task = renderer.render(input) { outcomes.add(it) }
        assertTrue(outcomes.isEmpty())
        runCurrent()
        task.cancel()
        renderer.close()
        runCurrent()
        assertEquals(1, outcomes.size)
        assertEquals("rendered", outcomes.single().outcome)
        assertEquals("html", outcomes.single().html)
        assertEquals(200, outcomes.single().httpStatus)
        assertNull(outcomes.single().errorCode)
    }

    @Test fun immediateCancelWinsAndCallbackSurvivesCancellation() = runTest {
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
        renderer.close()
    }

    @Test fun cancelDiscardsUncooperativeLateHtmlAndDoesNotCancelOtherRequest() = runTest {
        val gate = CompletableDeferred<Unit>()
        val outcomes = mutableListOf<String>()
        var calls = 0
        val renderer = renderer {
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
        renderer.close()
    }

    @Test fun closeIsIdempotentCancelsPendingAndRejectsFutureStaticCalls() = runTest {
        var closed = 0
        val outcomes = mutableListOf<PopupRenderOutput>()
        val renderer = renderer(close = { closed++ }) { awaitCancellation() }
        renderer.render(input) { outcomes.add(it) }
        runCurrent()
        renderer.close()
        renderer.close()
        assertTrue(outcomes.isEmpty())
        renderer.render(PopupRenderInput(null, null, null, null, null, null)) { outcomes.add(it) }
        runCurrent()
        assertEquals(listOf("cancelled", "failed"), outcomes.map { it.outcome })
        assertEquals("renderer_closed", outcomes.last().errorCode)
        assertEquals(1, closed)
    }

    @Test fun deadlineCancelsWorkAndProducesClientTimeoutOnlyOnce() = runTest {
        var requestCancelled = false
        val outcomes = mutableListOf<PopupRenderOutput>()
        val renderer = renderer {
            try { awaitCancellation() } finally { requestCancelled = true }
        }
        val task = renderer.render(input) { outcomes.add(it) }
        runCurrent()
        advanceTimeBy(20000)
        runCurrent()
        task.cancel()
        renderer.close()
        runCurrent()
        assertTrue(requestCancelled)
        assertEquals(1, outcomes.size)
        assertEquals("client_timeout", outcomes.single().errorCode)
        assertNull(outcomes.single().httpStatus)
    }

    @Test fun resumedRuntimeRejectsLateSuccessEvenBeforeTimerRuns() = runTest {
        var monotonic = 0L
        val outcomes = mutableListOf<PopupRenderOutput>()
        val renderer = renderer(clock = { monotonic }) {
            monotonic = 20001
            PopupRenderResult.Rendered("late html")
        }
        renderer.render(input) { outcomes.add(it) }
        runCurrent()
        assertEquals("client_timeout", outcomes.single().errorCode)
        assertNull(outcomes.single().html)
        renderer.close()
    }

    @Test fun successFinalizedWithinBudgetSurvivesDelayedCallbackDelivery() = runTest {
        var monotonic = 0L
        val outcomes = mutableListOf<PopupRenderOutput>()
        val renderer = renderer(clock = {
            val observed = monotonic
            // The final deadline read observes 19.999s; callback dispatch resumes after 21s.
            if (monotonic == 19999L) monotonic = 21000L
            observed
        }) {
            monotonic = 19999L
            PopupRenderResult.Rendered("html")
        }
        val task = renderer.render(input) { outcomes.add(it) }
        runCurrent()
        assertEquals(21000L, monotonic)
        assertEquals("rendered", outcomes.single().outcome)
        task.cancel()
        renderer.close()
        runCurrent()
        assertEquals(1, outcomes.size)
    }

    @Test fun elapsedBudgetBeforeWorkerStartsAvoidsRepositoryCall() = runTest {
        var monotonic = 0L
        val outcomes = mutableListOf<PopupRenderOutput>()
        val renderer = renderer(clock = { monotonic }) { error("deadline already expired") }
        renderer.render(input) { outcomes.add(it) }
        monotonic = 20000
        runCurrent()
        assertEquals("client_timeout", outcomes.single().errorCode)
        renderer.close()
    }

    @Test fun completedHttpTimeoutKeepsServerStatusAfterDeadlineAndCancel() = runTest {
        for ((code, status) in listOf("request_timeout" to 408, "popup_render_timeout" to 504)) {
            val outcomes = mutableListOf<PopupRenderOutput>()
            val renderer = renderer { PopupRenderResult.Failed(code, status) }
            val task = renderer.render(input) { outcomes.add(it) }
            runCurrent()
            advanceTimeBy(20001)
            task.cancel()
            renderer.close()
            runCurrent()
            assertEquals(code, outcomes.single().errorCode)
            assertEquals(status, outcomes.single().httpStatus)
        }
    }

    @Test fun staticInvalidSkippedAndInternalFailureCompleteOnce() = runTest {
        val outcomes = mutableListOf<PopupRenderOutput>()
        val renderer = renderer { PopupRenderResult.Skipped }
        renderer.render(PopupRenderInput("static", null, null, null, null, "bad")) { outcomes.add(it) }
        renderer.render(PopupRenderInput("ssr", null, null, null, null, null)) { outcomes.add(it) }
        renderer.render(input) { outcomes.add(it) }
        assertTrue(outcomes.isEmpty())
        runCurrent()
        assertEquals(listOf("static", "failed", "skipped"), outcomes.map { it.outcome })
        assertEquals("invalid_request", outcomes[1].errorCode)
        assertEquals(204, outcomes[2].httpStatus)
        renderer.close()
        val broken = renderer { error("recoverable internal failure") }
        broken.render(input) { outcomes.add(it) }
        runCurrent()
        assertEquals("internal_error", outcomes.last().errorCode)
        broken.close()
    }

    @Test fun callbackExceptionDoesNotBecomeSecondCompletionOrBreakOtherRequests() = runTest {
        var calls = 0
        val renderer = renderer()
        renderer.render(input) { calls++; error("caller failure") }
        renderer.render(input) { calls++ }
        runCurrent()
        assertEquals(2, calls)
        renderer.close()
    }

    @Test fun callbackCanReenterRendererAndCloseOutsideStateLock() = runTest {
        val renderer = renderer()
        val outcomes = mutableListOf<String>()
        renderer.render(input) {
            outcomes.add(it.outcome)
            renderer.close()
            renderer.render(input) { closed -> outcomes.add(closed.errorCode!!) }
        }
        runCurrent()
        assertEquals(listOf("rendered", "renderer_closed"), outcomes)
    }
}
