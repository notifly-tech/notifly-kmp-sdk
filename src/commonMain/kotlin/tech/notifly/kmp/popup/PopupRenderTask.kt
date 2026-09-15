@file:OptIn(ExperimentalJsExport::class)
package tech.notifly.kmp.popup

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import tech.notifly.kmp.popup.internal.PlatformLock

@JsExport
class PopupRenderTask internal constructor(cancelAction: () -> Unit) {
    private val lock = PlatformLock()
    private var cancelAction: (() -> Unit)? = cancelAction

    fun cancel() {
        lock.withLock { cancelAction }?.invoke()
    }

    internal fun detach() {
        lock.withLock { cancelAction = null }
    }
}
