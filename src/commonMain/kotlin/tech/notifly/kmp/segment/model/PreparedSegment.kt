@file:OptIn(kotlin.js.ExperimentalJsExport::class)

package tech.notifly.kmp.segment.model

import tech.notifly.kmp.segment.Predicate
import tech.notifly.kmp.segment.SegmentCondition
import kotlin.js.JsExport

/** The immutable result of preparing a popup segment. It can be reused with independent evaluation contexts. */
@JsExport
class PreparedSegment internal constructor(
    internal val groups: List<List<SegmentCondition>?>?,
)

/** The immutable result of preparing one event trigger or cancellation configuration. */
@JsExport
class PreparedEventPredicates internal constructor(
    internal val names: List<List<Predicate>?>,
    internal val filters: List<List<Predicate>?>?,
)
