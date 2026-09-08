@file:OptIn(ExperimentalJsExport::class)

package tech.notifly.kmp.identity

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class UserIdTransitionDecision(
    val changed: Boolean,
    val shouldSync: Boolean,
    val shouldMerge: Boolean,
    val shouldClear: Boolean,
)

@JsExport
object UserIdTransitionPolicy {
    fun evaluate(
        previousUserId: String?,
        newUserId: String?,
    ): UserIdTransitionDecision {
        val changed = previousUserId != newUserId

        return UserIdTransitionDecision(
            changed = changed,
            shouldSync = changed,
            shouldMerge = changed && previousUserId == null && newUserId != null,
            shouldClear = newUserId == null,
        )
    }
}
