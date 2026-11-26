package tech.notifly.kmp.segment

import tech.notifly.kmp.segment.model.EvaluationContext
import tech.notifly.kmp.segment.model.EventHistory

internal const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L

private val countOperators = setOf("=", ">", ">=", "<", "<=")
private val filterKeys = setOf("comparison_parameter_field_name", "comparison_operator", "comparison_value")

internal fun compileEventCount(wire: Map<*, *>): SegmentCondition? {
    val dynamicTarget = wire["useEventParamsAsConditionValue"]
    if (dynamicTarget != null && dynamicTarget != false) return null
    if (filterKeys.any { wire[it] != null }) return null
    val event = wire.nonemptyString("event") ?: return null
    val operator = wire["operator"] as? String ?: return null
    if (operator !in countOperators) return null
    val type = wire["valueType"]
    if (type != null && type != "INT") return null
    val minimum = safeCount(wire["value"]) ?: return null
    val days =
        when (wire["event_condition_type"]) {
            "count X" -> null
            "count X in Y days" -> safeCount(wire["secondary_value"]) ?: return null
            else -> return null
        }
    return EventCountCondition(event, operator, minimum, days)
}

private class EventCountCondition(
    private val event: String,
    private val operator: String,
    private val threshold: Long,
    private val days: Long?,
) : SegmentCondition {
    override fun matches(context: EvaluationContext): Boolean {
        val history = context.eventHistory[event] ?: return false
        val window = days?.let { SeoulCalendar.dayAt(context.evaluationAt).let { today -> (today - it)..today } }
        if (!history.covers(window)) return false
        val count = history.sum(window) ?: return false
        return when (operator) {
            "=" -> count == threshold
            ">" -> count > threshold
            ">=" -> count >= threshold
            "<" -> count < threshold
            "<=" -> count <= threshold
            else -> false
        }
    }
}

private fun EventHistory.covers(window: LongRange?): Boolean {
    if (allTime) return true
    if (window == null) return false
    val first = coveredFrom?.let(SeoulCalendar::parseDate) ?: return false
    val last = coveredThrough?.let(SeoulCalendar::parseDate) ?: return false
    return first <= window.first && last >= window.last
}

private fun EventHistory.sum(window: LongRange?): Long? {
    var result = 0L
    for (row in rows) {
        val date = SeoulCalendar.parseDate(row.kstDate) ?: return null
        if (row.count !in 0..MAX_SAFE_INTEGER) return null
        if (window == null || date in window) {
            if (row.count > MAX_SAFE_INTEGER - result) return null
            result += row.count
        }
    }
    return result
}

internal fun safeCount(value: Any?): Long? {
    val count = (value as? Number)?.toDouble() ?: return null
    if (count !in 0.0..MAX_SAFE_INTEGER.toDouble()) return null
    val integer = count.toLong()
    return integer.takeIf { it.toDouble() == count }
}
