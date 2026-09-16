@file:OptIn(ExperimentalJsExport::class)
package tech.notifly.kmp.popup

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlinx.coroutines.*
import tech.notifly.kmp.popup.domain.model.*
import tech.notifly.kmp.popup.domain.usecase.RenderPopupUseCase
import tech.notifly.kmp.popup.internal.PlatformLock
import tech.notifly.kmp.popup.model.*

@JsExport
class PopupRenderer internal constructor(
    private val useCase: RenderPopupUseCase,
    private val closeResources: () -> Unit,
    private val dispatcher: CoroutineDispatcher,
) {
    private val lock = PlatformLock()
    private val pending = mutableSetOf<Pending>()
    private var closed = false

    fun render(input: PopupRenderInput, onComplete: (PopupRenderOutput) -> Unit): PopupRenderTask {
        val state = Pending(onComplete)
        val task = PopupRenderTask { finish(state, PopupRenderResult.Cancelled) }
        val scope = CoroutineScope(dispatcher)
        val accepted = lock.withLock {
            state.task = task
            if (closed) false
            else {
                pending.add(state)
                state.work = scope.launch(start = CoroutineStart.LAZY) {
                    val result = try {
                        useCase.render(PopupRenderRequest(input.templateRenderingMode, input.campaignId, input.notiflyUserId, input.deviceId, input.eventName, input.eventParamsJson))
                    } catch (error: CancellationException) {
                        return@launch
                    } catch (error: Exception) {
                        PopupRenderResult.Failed("internal_error")
                    }
                    finish(state, result)
                }
                true
            }
        }
        if (accepted) {
            // A cancelled lazy job stays cancelled if cancel/close wins before launch.
            val work = lock.withLock { state.work }
            work?.start()
        } else finish(state, PopupRenderResult.Failed("renderer_closed"))
        return task
    }

    fun close() {
        val completions = lock.withLock {
            if (closed) null else {
                closed = true
                pending.toList().mapNotNull { settle(it, PopupRenderResult.Cancelled) }
            }
        } ?: return
        completions.forEach { deliver(it) }
        closeResources()
    }

    private fun finish(state: Pending, proposed: PopupRenderResult) {
        val completion = lock.withLock { settle(state, proposed) } ?: return
        deliver(completion)
    }

    // The caller holds the renderer lock. close settles every pending task in one transition.
    private fun settle(state: Pending, proposed: PopupRenderResult): Completion? {
        val callback = state.callback ?: return null
        val completion = Completion(callback, proposed.toOutput(), state.work)
        state.callback = null
        state.work = null
        state.task?.detach()
        state.task = null
        pending.remove(state)
        return completion
    }

    private fun deliver(completion: Completion) {
        completion.work?.cancel()
        // This delivery job is independent of the request job, including cancel and close.
        CoroutineScope(dispatcher).launch {
            yield()
            try { completion.callback(completion.output) } catch (error: Throwable) {
                // A caller exception must neither escape into the host nor become a second result.
                // Native JS Error objects are Throwables but not Kotlin Exceptions.
            }
        }
    }

}

private class Pending(var callback: ((PopupRenderOutput) -> Unit)?) {
    var task: PopupRenderTask? = null
    var work: Job? = null
}

private class Completion(val callback: (PopupRenderOutput) -> Unit, val output: PopupRenderOutput, val work: Job?)

private fun PopupRenderResult.toOutput(): PopupRenderOutput = when (this) {
    PopupRenderResult.Static -> PopupRenderOutput("static", null, null, null)
    is PopupRenderResult.Rendered -> PopupRenderOutput("rendered", html, null, 200)
    PopupRenderResult.Skipped -> PopupRenderOutput("skipped", null, null, 204)
    is PopupRenderResult.Failed -> PopupRenderOutput("failed", null, errorCode, httpStatus)
    PopupRenderResult.Cancelled -> PopupRenderOutput("cancelled", null, null, null)
}
