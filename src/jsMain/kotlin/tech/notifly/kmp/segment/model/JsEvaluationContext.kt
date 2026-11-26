@file:OptIn(kotlin.js.ExperimentalJsExport::class)

package tech.notifly.kmp.segment.model

import kotlin.js.JsExport

/** A prepared JavaScript input snapshot that can be shared across evaluations of the same event. */
@JsExport
class JsEvaluationContext internal constructor(
    internal val context: EvaluationContext,
)
