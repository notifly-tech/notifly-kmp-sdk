@file:OptIn(ExperimentalJsExport::class)

package tech.notifly.kmp.identity

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Actions the host SDK should take after evaluating a user ID transition.
 *
 * Obtain this result from [UserIdTransitionPolicy.evaluate]; hosts normally do not construct it.
 * [changed] and [shouldSync] indicate different IDs, [shouldMerge] indicates an anonymous-to-identified
 * transition, and [shouldClear] indicates a null new ID. No SDK state is changed by reading this result.
 *
 * Android / Kotlin (`decision` is the result of `evaluate`):
 * ```kotlin
 * println(decision.changed)
 * println(decision.shouldSync)
 * println(decision.shouldMerge)
 * println(decision.shouldClear)
 * ```
 *
 * iOS / Swift:
 * ```swift
 * print(decision.changed, decision.shouldSync, decision.shouldMerge, decision.shouldClear)
 * ```
 *
 * JavaScript:
 * ```javascript
 * console.log(decision.changed, decision.shouldSync, decision.shouldMerge, decision.shouldClear);
 * ```
 */
@JsExport
class UserIdTransitionDecision(
    val changed: Boolean,
    val shouldSync: Boolean,
    val shouldMerge: Boolean,
    val shouldClear: Boolean,
)

/** Computes user ID transition decisions without performing platform SDK side effects. */
@JsExport
object UserIdTransitionPolicy {
    /**
     * Compares the previous and new IDs and returns the actions for the host SDK to perform.
     *
     * A null previous ID represents an anonymous user. A null new ID requests clearing identity.
     * Values are compared as supplied, without trimming or other normalization.
     *
     * Android / Kotlin:
     * ```kotlin
     * val decision = UserIdTransitionPolicy.evaluate(previousUserId = null, newUserId = "user-123")
     * ```
     *
     * iOS / Swift (after `import NotiflyKMP`):
     * ```swift
     * let decision = UserIdTransitionPolicy.shared.evaluate(previousUserId: nil, newUserId: "user-123")
     * ```
     *
     * JavaScript (`sdk` is the imported KMP module):
     * ```javascript
     * const identity = sdk.tech.notifly.kmp.identity;
     * const decision = identity.UserIdTransitionPolicy.evaluate(null, "user-123");
     * ```
     */
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
