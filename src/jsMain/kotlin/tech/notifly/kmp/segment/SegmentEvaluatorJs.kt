@file:OptIn(kotlin.js.ExperimentalJsExport::class)

package tech.notifly.kmp.segment

import tech.notifly.kmp.core.util.snapshotJsData
import tech.notifly.kmp.segment.model.EventCountRow
import tech.notifly.kmp.segment.model.EventHistory
import tech.notifly.kmp.segment.model.JsEvaluationContext
import tech.notifly.kmp.segment.model.PreparedEventPredicates
import tech.notifly.kmp.segment.model.PreparedSegment
import kotlin.js.JsExport
import kotlin.js.JsName

/** Prepares a popup segment from the JavaScript campaign wire object. */
@JsExport
@JsName("compilePopupSegment")
fun compilePopupSegmentJs(wire: dynamic): PreparedSegment = compilePopupSegment(snapshotJsData(wire).asSegmentRecord())

/**
 * Snapshots the JavaScript SDK's evaluation inputs.
 *
 * [input] contains an integer `evaluationAt` in epoch milliseconds, optional attribute scopes `user`,
 * `userMetadata`, and `device` with `values` and optional `unavailableKeys`, and an optional `event` with
 * `name` and `parameters`. Optional `eventHistory` is keyed by event name and uses the fields in
 * [EventHistory] and [EventCountRow]. An invalid timestamp returns null.
 */
@JsExport
@JsName("createEvaluationContext")
fun createEvaluationContextJs(input: dynamic): JsEvaluationContext? =
    decodeEvaluationContext(snapshotJsData(input))?.let(::JsEvaluationContext)

/** Evaluates the prepared segment against a JavaScript snapshot. A null snapshot evaluates to false. */
@JsExport
@JsName("evaluateSegment")
fun evaluateSegmentJs(
    segment: PreparedSegment,
    context: JsEvaluationContext?,
): Boolean = context != null && evaluateSegment(segment, context.context)

/** Prepares event predicates using `legacyEventName`, `nameConditions`, and `parameterFilters`. */
@JsExport
@JsName("compileEventPredicates")
fun compileEventPredicatesJs(wire: dynamic): PreparedEventPredicates =
    compileEventPredicates(decodeEventPredicateInput(snapshotJsData(wire)))

/** Evaluates a JavaScript event with `name` and optional `parameters`. A malformed event evaluates to false. */
@JsExport
@JsName("matchesEvent")
fun matchesEventJs(
    predicates: PreparedEventPredicates,
    event: dynamic,
): Boolean = snapshotJsData(event).decodeEventSnapshot()?.let { matchesEvent(predicates, it) } == true
