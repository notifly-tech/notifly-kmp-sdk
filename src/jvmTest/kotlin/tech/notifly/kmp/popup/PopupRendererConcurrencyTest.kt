package tech.notifly.kmp.popup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tech.notifly.kmp.popup.domain.model.PopupRenderResult
import tech.notifly.kmp.popup.domain.repository.PopupRenderRepository
import tech.notifly.kmp.popup.domain.usecase.RenderPopupUseCase
import tech.notifly.kmp.popup.model.PopupRenderInput
import tech.notifly.kmp.popup.model.PopupRenderOutput
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PopupRendererConcurrencyTest {
    private fun renderer(block: suspend () -> PopupRenderResult) =
        PopupRenderer(
            RenderPopupUseCase("0123456789abcdef0123456789abcdef", "sdk", PopupRenderRepository { block() }),
            {},
            Dispatchers.Default,
        )

    private fun input(params: Map<String, Any?> = emptyMap()) =
        PopupRenderInput("ssr", "campaign", "user", "device", "open", params)

    @Test
    fun concurrentCancelCompletionAndCloseDeliverExactlyOnce() =
        runBlocking {
            repeat(100) {
                val ready = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val delivered = CompletableDeferred<PopupRenderOutput>()
                val callbacks = AtomicInteger()
                val renderer =
                    renderer {
                        ready.complete(Unit)
                        release.await()
                        PopupRenderResult.Rendered("html")
                    }
                val task =
                    renderer.render(input()) {
                        callbacks.incrementAndGet()
                        delivered.complete(it)
                    }
                ready.await()
                val cancel = launch(Dispatchers.Default) { task.cancel() }
                val close = launch(Dispatchers.Default) { renderer.close() }
                release.complete(Unit)
                cancel.join()
                close.join()
                val result = delivered.await()
                assertTrue(result.outcome == "cancelled" || result.outcome == "rendered")
                assertEquals(1, callbacks.get())
            }
        }

    @Test
    fun callbackRunsWithoutHoldingStateLockOnAnotherThread() {
        val renderer = renderer { PopupRenderResult.Skipped }
        val done = CountDownLatch(1)
        var acquired = false
        renderer.render(input()) {
            val closed = CountDownLatch(1)
            Thread {
                renderer.close()
                closed.countDown()
            }.start()
            acquired = closed.await(2, TimeUnit.SECONDS)
            done.countDown()
        }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertTrue(acquired)
    }

    @Test
    fun retainedCompletedHandleDoesNotRetainInputCallbackOrHtml() {
        val (handle, references) = completedHandle()
        repeat(50) {
            if (references.all { it.get() == null }) return@repeat
            System.gc()
            Thread.sleep(10)
        }
        assertTrue(references.all { it.get() == null }, "Completed handle retained request data")
        handle.cancel()
    }

    private fun completedHandle(): Pair<PopupRenderTask, List<WeakReference<Any>>> =
        runBlocking {
            val html = String(CharArray(100000) { 'h' })
            val input = input(mapOf("text" to String(CharArray(100000) { 'e' })))
            val callbackOwner = Any()
            val delivered = CompletableDeferred<Unit>()
            val renderer = renderer { PopupRenderResult.Rendered(html) }
            val handle =
                renderer.render(input) {
                    callbackOwner.hashCode()
                    delivered.complete(Unit)
                }
            delivered.await()
            renderer.close()
            handle to listOf(WeakReference<Any>(html), WeakReference<Any>(input), WeakReference(callbackOwner))
        }
}
