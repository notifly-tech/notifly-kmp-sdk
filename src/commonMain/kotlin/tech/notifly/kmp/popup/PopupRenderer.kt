@file:OptIn(ExperimentalJsExport::class)

package tech.notifly.kmp.popup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import tech.notifly.kmp.popup.domain.model.PopupRenderRequest
import tech.notifly.kmp.popup.domain.model.PopupRenderResult
import tech.notifly.kmp.popup.domain.usecase.RenderPopupUseCase
import tech.notifly.kmp.popup.internal.PlatformLock
import tech.notifly.kmp.popup.model.PopupRenderInput
import tech.notifly.kmp.popup.model.PopupRenderOutput
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Resolves popup rendering results without displaying UI or loading a WebView.
 *
 * Create instances with [PopupFactory.create] and call [close] when the owning SDK releases them.
 */
@JsExport
class PopupRenderer internal constructor(
    private val useCase: RenderPopupUseCase,
    private val closeResources: () -> Unit,
    private val dispatcher: CoroutineDispatcher,
) {
    private val lock = PlatformLock()
    private val pending = mutableSetOf<Pending>()
    private var closed = false

    /**
     * Resolves [input] and schedules one terminal result through [onComplete].
     *
     * Only the exact mode `ssr` requests server-rendered HTML. Other modes return `static` without
     * validating the request or accessing the network. A closed renderer instead fails with
     * `renderer_closed`, regardless of mode.
     *
     * Completion, task cancellation, and [close] compete for the first terminal result. The callback
     * runs asynchronously outside the state lock, with no main-thread guarantee. Callback failures
     * are contained and do not produce another result.
     */
    fun render(
        input: PopupRenderInput,
        onComplete: (PopupRenderOutput) -> Unit,
    ): PopupRenderTask {
        val state = Pending(onComplete)
        val task = PopupRenderTask { finish(state, PopupRenderResult.Cancelled) }
        val scope = CoroutineScope(dispatcher)
        val accepted =
            lock.withLock {
                state.task = task
                if (closed) {
                    false
                } else {
                    pending.add(state)
                    state.work =
                        scope.launch(start = CoroutineStart.LAZY) {
                            val result =
                                try {
                                    useCase.render(
                                        PopupRenderRequest(
                                            input.templateRenderingMode,
                                            input.campaignId,
                                            input.notiflyUserId,
                                            input.deviceId,
                                            input.eventName,
                                            input.eventParamsJson,
                                        ),
                                    )
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
        } else {
            finish(state, PopupRenderResult.Failed("renderer_closed"))
        }
        return task
    }

    /**
     * Cancels pending renders and releases owned resources.
     *
     * Subsequent calls to [render] fail with `renderer_closed`. Repeated calls have no additional
     * effect. This method does not wait for callbacks; already settled results are still delivered.
     */
    fun close() {
        val completions =
            lock.withLock {
                if (closed) {
                    null
                } else {
                    closed = true
                    pending.toList().mapNotNull { settle(it, PopupRenderResult.Cancelled) }
                }
            } ?: return
        completions.forEach { deliver(it) }
        closeResources()
    }

    private fun finish(
        state: Pending,
        proposed: PopupRenderResult,
    ) {
        val completion = lock.withLock { settle(state, proposed) } ?: return
        deliver(completion)
    }

    /**
     * Claims the first terminal result while the caller holds the renderer lock.
     *
     * Keeping delivery separate lets [close] settle all pending requests in one locked transition.
     */
    private fun settle(
        state: Pending,
        proposed: PopupRenderResult,
    ): Completion? {
        val callback = state.callback ?: return null
        val completion = Completion(callback, proposed.toOutput(), state.work)
        state.callback = null
        state.work = null
        state.task?.detach()
        state.task = null
        pending.remove(state)
        return completion
    }

    /**
     * Delivers a settled result in a job independent of request cancellation and renderer shutdown.
     *
     * Callback failures must neither escape into the host nor become a second result. Catching
     * [Throwable] also contains native JavaScript errors that are not Kotlin exceptions.
     */
    private fun deliver(completion: Completion) {
        completion.work?.cancel()
        CoroutineScope(dispatcher).launch {
            yield()
            try {
                completion.callback(completion.output)
            } catch (error: Throwable) {
            }
        }
    }
}

private class Pending(
    var callback: ((PopupRenderOutput) -> Unit)?,
) {
    var task: PopupRenderTask? = null
    var work: Job? = null
}

private class Completion(
    val callback: (PopupRenderOutput) -> Unit,
    val output: PopupRenderOutput,
    val work: Job?,
)

private fun PopupRenderResult.toOutput(): PopupRenderOutput =
    when (this) {
        PopupRenderResult.Static -> PopupRenderOutput("static", null, null, null)
        is PopupRenderResult.Rendered -> PopupRenderOutput("rendered", html, null, 200)
        PopupRenderResult.Skipped -> PopupRenderOutput("skipped", null, null, 204)
        is PopupRenderResult.Failed -> PopupRenderOutput("failed", null, errorCode, httpStatus)
        PopupRenderResult.Cancelled -> PopupRenderOutput("cancelled", null, null, null)
    }
