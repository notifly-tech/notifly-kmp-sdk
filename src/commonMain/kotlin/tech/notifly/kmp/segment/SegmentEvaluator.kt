package tech.notifly.kmp.segment

import tech.notifly.kmp.segment.model.AttributeSnapshot
import tech.notifly.kmp.segment.model.EvaluationContext
import tech.notifly.kmp.segment.model.EventPredicateInput
import tech.notifly.kmp.segment.model.EventSnapshot
import tech.notifly.kmp.segment.model.PreparedEventPredicates
import tech.notifly.kmp.segment.model.PreparedSegment

/**
 * Prepares a popup segment from its wire representation for repeated evaluation.
 *
 * [wire] must contain a `groups` list. An otherwise valid segment with an empty list matches all contexts.
 * A missing or malformed list matches none. An invalid condition or group does not affect other OR groups.
 */
fun compilePopupSegment(wire: Map<String, Any?>?): PreparedSegment {
    val groups = wire?.get("groups") as? List<*> ?: return PreparedSegment(null)
    if (!allowsOperator(wire["group_operator"], "OR")) return PreparedSegment(null)
    val additional = wire["additional_conditions"]
    if (additional != null && (additional !is List<*> || additional.isNotEmpty())) {
        return PreparedSegment(null)
    }

    val prepared =
        groups.map { rawGroup ->
            val group = rawGroup as? Map<*, *> ?: return@map null
            if (!allowsOperator(group["condition_operator"], "AND")) return@map null
            compileGroup(group["conditions"], ::compileSegmentCondition)
        }
    return PreparedSegment(prepared)
}

/** Returns whether [segment] matches [context] using only the supplied data and reference time. */
fun evaluateSegment(
    segment: PreparedSegment,
    context: EvaluationContext,
): Boolean {
    val groups = segment.groups ?: return false
    return groups.isEmpty() || anyMatchingGroup(groups) { it.matches(context) }
}

/** Prepares the name and parameter groups for a single trigger or cancellation rule. */
fun compileEventPredicates(wire: EventPredicateInput): PreparedEventPredicates {
    val names =
        if (wire.nameConditions == null) {
            val legacy = wire.legacyEventName?.takeIf { it.isNotEmpty() }
            val comparison = legacy?.let { compileComparison("=", "TEXT", it) }
            if (comparison == null) emptyList() else listOf(listOf(Predicate(null, comparison)))
        } else {
            compileGroups(wire.nameConditions, ::compileEventName) ?: emptyList()
        }
    val filters =
        if (wire.parameterFilters == null) {
            emptyList()
        } else {
            compileGroups(wire.parameterFilters, ::compileFilter)
        }
    return PreparedEventPredicates(names, filters)
}

/** Returns whether [event] has a nonempty name and satisfies all restrictions in [predicates]. */
fun matchesEvent(
    predicates: PreparedEventPredicates,
    event: EventSnapshot,
): Boolean {
    if (event.name.isEmpty()) return false
    val filters = predicates.filters ?: return false
    return anyMatchingGroup(predicates.names) { it.matches(event) } &&
        (filters.isEmpty() || anyMatchingGroup(filters) { it.matches(event) })
}

internal sealed interface SegmentCondition {
    fun matches(context: EvaluationContext): Boolean
}

internal class Predicate(
    private val key: String?,
    private val comparison: Comparison,
) {
    fun matches(event: EventSnapshot): Boolean {
        if (key != null && key in event.unavailableParameterKeys) return false
        val source = if (key == null) event.name else event.parameters[key]
        return comparison.matches(source)
    }
}

private enum class AttributeScope {
    USER,
    USER_METADATA,
    DEVICE,
    ;

    fun select(context: EvaluationContext): AttributeSnapshot? =
        when (this) {
            USER -> context.user
            USER_METADATA -> context.userMetadata
            DEVICE -> context.device
        }
}

private class AttributeCondition(
    private val scope: AttributeScope,
    private val key: String,
    private val parameter: String?,
    private val comparison: Comparison,
) : SegmentCondition {
    override fun matches(context: EvaluationContext): Boolean {
        val snapshot = scope.select(context) ?: return false
        if (key in snapshot.unavailableKeys) return false
        val event = context.event
        val target =
            if (parameter == null) {
                null
            } else {
                if (event == null || event.name.isEmpty() || parameter in event.unavailableParameterKeys) return false
                event.parameters[parameter]
            }
        return comparison.matches(snapshot.values[key], target)
    }
}

private val deviceVersions = setOf("os_version", "app_version", "sdk_version")
private val deviceAttributes = deviceVersions + setOf("platform", "sdk_type")
private val arrayOperators = setOf("@>", "NOT_INCLUDE", "CONTAINS_ANY_OF")
private val eventNameOperators =
    setOf(
        "=",
        "!=",
        "starts_with",
        "does_not_start_with",
        "ends_with",
        "does_not_end_with",
        "contains",
        "does_not_contain",
        "matches_regex",
        "does_not_match_regex",
    )

private fun compileSegmentCondition(wire: Map<*, *>): SegmentCondition? {
    if (wire["evaluation_status"] != null) return null
    val unit = wire["unit"]
    if (unit == "event") return compileEventCount(wire)
    val scope =
        when (unit) {
            "user" -> AttributeScope.USER
            "user_metadata" -> AttributeScope.USER_METADATA
            "device" -> AttributeScope.DEVICE
            else -> return null
        }
    val attribute = wire.nonemptyString("attribute") ?: return null
    val operator = wire["operator"] as? String ?: return null
    val type = wire["valueType"] as? String
    val exists = operator == "IS_NULL" || operator == "IS_NOT_NULL"
    if (!supportsAttribute(scope, attribute, operator, type, exists)) return null
    val parameter =
        if (exists) {
            null
        } else {
            when (wire["useEventParamsAsConditionValue"]) {
                null, false -> null
                true -> wire.nonemptyString("comparison_parameter") ?: return null
                else -> return null
            }
        }
    val comparison =
        compileComparison(
            operator,
            type,
            wire["value"],
            dynamicTarget = parameter != null,
            version = scope == AttributeScope.DEVICE && attribute in deviceVersions,
        ) ?: return null
    return AttributeCondition(scope, attribute, parameter, comparison)
}

private fun supportsAttribute(
    scope: AttributeScope,
    attribute: String,
    operator: String,
    type: String?,
    exists: Boolean,
): Boolean {
    val expectedType =
        when (scope) {
            AttributeScope.USER -> {
                return true
            }

            AttributeScope.USER_METADATA -> {
                when (attribute) {
                    "external_user_id" -> "TEXT"
                    "random_bucket_number" -> "INT"
                    else -> return false
                }
            }

            AttributeScope.DEVICE -> {
                if (attribute in deviceAttributes) "TEXT" else return false
            }
        }
    return operator !in arrayOperators && (exists || type == expectedType)
}

private fun compileEventName(wire: Map<*, *>): Predicate? {
    if (wire["type"] != "event_name" || wire["evaluation_status"] != null) return null
    val operator = wire["operator"] as? String ?: return null
    if (operator !in eventNameOperators) return null
    val comparison = compileComparison(if (operator == "!=") "<>" else operator, "TEXT", wire["operand"])
    return comparison?.let { Predicate(null, it) }
}

private fun compileFilter(wire: Map<*, *>): Predicate? {
    if (wire["evaluation_status"] != null) return null
    val key = wire.nonemptyString("key") ?: return null
    val comparison = compileComparison(wire["operator"] as? String, wire["value_type"] as? String, wire["value"])
    return comparison?.let { Predicate(key, it) }
}

private fun allowsOperator(
    value: Any?,
    allowed: String,
): Boolean = value == null || value == allowed

internal fun Map<*, *>.nonemptyString(key: String): String? = (get(key) as? String)?.takeIf { it.isNotEmpty() }

private fun <T> compileGroups(
    value: Any,
    compile: (Map<*, *>) -> T?,
): List<List<T>?>? = (value as? List<*>)?.map { compileGroup(it, compile) }

private fun <T> compileGroup(
    value: Any?,
    compile: (Map<*, *>) -> T?,
): List<T>? {
    val group = value as? List<*> ?: return null
    if (group.isEmpty()) return null

    return group.map { condition ->
        val input = condition as? Map<*, *> ?: return null
        invalidOnFailure { compile(input) } ?: return null
    }
}

private inline fun <T> anyMatchingGroup(
    groups: List<List<T>?>,
    matches: (T) -> Boolean,
): Boolean = groups.any { group -> group?.all { invalidOnFailure { matches(it) } == true } == true }

private inline fun <T> invalidOnFailure(block: () -> T): T? =
    try {
        block()
    } catch (_: Exception) {
        null
    }
