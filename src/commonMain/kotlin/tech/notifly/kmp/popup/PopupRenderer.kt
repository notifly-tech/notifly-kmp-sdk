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
 * Create instances with [PopupFactory.create] and reuse them without an explicit close step.
 * Cancel individual requests through the returned [PopupRenderTask].
 */
@JsExport
class PopupRenderer internal constructor(
    private val useCase: RenderPopupUseCase,
    private val dispatcher: CoroutineDispatcher,
) {
    /**
     * Resolves [input] and schedules one terminal result through [onComplete].
     *
     * Only the exact mode `ssr` requests server-rendered HTML. Other modes return `static` without
     * validating the request or accessing the network.
     *
     * Completion and task cancellation compete for the first terminal result. The callback
     * runs asynchronously outside the state lock, with no main-thread guarantee. Callback failures
     * are contained and do not produce another result.
     *
     * These examples use a renderer from [PopupFactory.create] and a [PopupRenderInput]. Keep the
     * returned task to cancel this request. Dispatch UI updates to the platform's UI thread separately.
     *
     * Android / Kotlin:
     * ```kotlin
     * val task = renderer.render(input) { output ->
     *     println(output.outcome)
     * }
     * ```
     *
     * iOS / Swift:
     * ```swift
     * let task = renderer.render(input: input) { output in
     *     print(output.outcome)
     * }
     * ```
     *
     * JavaScript (create `input` with `popup.createPopupRenderInput`):
     * ```javascript
     * const task = renderer.render(input, (output) => {
     *   console.log(output.outcome);
     * });
     * ```
     */
    fun render(
        input: PopupRenderInput,
        onComplete: (PopupRenderOutput) -> Unit,
    ): PopupRenderTask {
        val state = Pending(onComplete)
        val task = PopupRenderTask { finish(state, PopupRenderResult.Cancelled) }
        state.task = task
        val work =
            CoroutineScope(dispatcher).launch(start = CoroutineStart.LAZY) {
                val result =
                    try {
                        useCase.render(
                            PopupRenderRequest(
                                input.templateRenderingMode,
                                input.campaignId,
                                input.notiflyUserId,
                                input.deviceId,
                                input.eventName,
                                input.eventParams,
                                input.hasInvalidEventParams,
                            ),
                        )
                    } catch (error: CancellationException) {
                        return@launch
                    } catch (error: Exception) {
                        PopupRenderResult.Failed("internal_error")
                    }
                finish(state, result)
            }
        state.work = work
        work.start()
        return task
    }

    /** Claims the first terminal result under the request lock, then delivers it outside the lock. */
    private fun finish(
        state: Pending,
        proposed: PopupRenderResult,
    ) {
        val completion =
            state.lock.withLock {
                val callback = state.callback ?: return@withLock null
                val completion = Completion(callback, proposed.toOutput(), state.work)
                state.callback = null
                state.work = null
                state.task?.detach()
                state.task = null
                completion
            } ?: return
        deliver(completion)
    }

    /**
     * Delivers a settled result in a job independent of request cancellation.
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
    val lock = PlatformLock()
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
