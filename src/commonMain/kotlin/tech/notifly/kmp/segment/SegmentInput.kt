package tech.notifly.kmp.segment

import tech.notifly.kmp.core.util.UnreadableValue
import tech.notifly.kmp.segment.model.AttributeSnapshot
import tech.notifly.kmp.segment.model.EvaluationContext
import tech.notifly.kmp.segment.model.EventCountRow
import tech.notifly.kmp.segment.model.EventHistory
import tech.notifly.kmp.segment.model.EventPredicateInput
import tech.notifly.kmp.segment.model.EventSnapshot

/** Decodes normalized host values into a segment snapshot. Invalid time makes the snapshot unavailable. */
internal fun decodeEvaluationContext(input: Any?): EvaluationContext? {
    val snapshot = input.asSegmentRecord() ?: return null
    val timestamp = (snapshot["evaluationAt"] as? Number)?.toDouble() ?: return null
    val limit = MAX_SAFE_INTEGER.toDouble()
    if (timestamp !in -limit..limit) return null
    val milliseconds = timestamp.toLong()
    if (milliseconds.toDouble() != timestamp) return null
    return EvaluationContext(
        evaluationAt = milliseconds,
        user = snapshot["user"].toAttributeSnapshot(),
        userMetadata = snapshot["userMetadata"].toAttributeSnapshot(),
        device = snapshot["device"].toAttributeSnapshot(),
        event = snapshot["event"].decodeEventSnapshot(),
        eventHistory = snapshot["eventHistory"].toEventHistory(),
    )
}

internal fun decodeEventPredicateInput(input: Any?): EventPredicateInput {
    val data = input.asSegmentRecord()
    return EventPredicateInput(
        legacyEventName = data?.get("legacyEventName") as? String,
        nameConditions = data?.get("nameConditions"),
        parameterFilters = data?.get("parameterFilters"),
    )
}

/** Host data adapters produce record maps with string keys. */
@Suppress("UNCHECKED_CAST")
internal fun Any?.asSegmentRecord(): Map<String, Any?>? = this as? Map<String, Any?>

private fun Any?.toAttributeSnapshot(): AttributeSnapshot? {
    val scope = asSegmentRecord() ?: return null
    val values = scope["values"].asSegmentRecord() ?: return null
    val keys =
        when (val supplied = scope["unavailableKeys"]) {
            null -> emptySet()
            is List<*> -> supplied.map { it as? String ?: return null }.toSet()
            else -> return null
        }
    return AttributeSnapshot(values, keys + values.unavailableKeys())
}

internal fun Any?.decodeEventSnapshot(): EventSnapshot? {
    val event = asSegmentRecord() ?: return null
    val name = event.nonemptyString("name") ?: return null
    val rawParameters = event["parameters"]
    val parameters = if (rawParameters == null) emptyMap() else rawParameters.asSegmentRecord() ?: return null
    return EventSnapshot(name, parameters, parameters.unavailableKeys())
}

private fun Map<String, Any?>.unavailableKeys(): Set<String> =
    filterValues { it === UnreadableValue || (it is List<*> && it.any { element -> element === UnreadableValue }) }.keys

private fun Any?.toEventHistory(): Map<String, EventHistory> {
    val history = asSegmentRecord() ?: return emptyMap()
    return buildMap {
        history.forEach { (name, value) ->
            value.decodeHistory()?.let { put(name, it) }
        }
    }
}

private fun Any?.decodeHistory(): EventHistory? {
    val history = asSegmentRecord() ?: return null
    val allTime = history["allTime"]
    if (allTime != null && allTime !is Boolean) return null
    val wireRows = history["rows"] as? List<*> ?: return null
    val rows =
        wireRows.map {
            val row = it.asSegmentRecord() ?: return null
            val date = row["kstDate"] as? String ?: return null
            val count = safeCount(row["count"]) ?: return null
            EventCountRow(date, count)
        }
    return EventHistory(
        rows = rows,
        allTime = allTime == true,
        coveredFrom = history["coveredFrom"] as? String,
        coveredThrough = history["coveredThrough"] as? String,
    )
}
