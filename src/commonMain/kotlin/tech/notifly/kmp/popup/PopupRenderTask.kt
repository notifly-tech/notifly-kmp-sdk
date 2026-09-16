@file:OptIn(ExperimentalJsExport::class)

package tech.notifly.kmp.popup

import tech.notifly.kmp.popup.internal.PlatformLock
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/** A cancellation handle for one [PopupRenderer.render] call. */
@JsExport
class PopupRenderTask internal constructor(
    cancelAction: () -> Unit,
) {
    private val lock = PlatformLock()
    private var cancelAction: (() -> Unit)? = cancelAction

    /**
     * Cancels this request if it has not already settled, without affecting other requests.
     *
     * Cancellation schedules a `cancelled` result through the original callback. A result that has
     * already won is preserved, even if its callback has not run yet. Repeated calls have no effect.
     *
     * Call this on the task returned by [PopupRenderer.render], for example when the host dismisses
     * the pending popup. Do not construct a task directly.
     *
     * Android / Kotlin:
     * ```kotlin
     * task.cancel()
     * ```
     *
     * iOS / Swift:
     * ```swift
     * task.cancel()
     * ```
     *
     * JavaScript:
     * ```javascript
     * task.cancel();
     * ```
     */
    fun cancel() {
        lock.withLock { cancelAction }?.invoke()
    }

    internal fun detach() {
        lock.withLock { cancelAction = null }
    }
}
