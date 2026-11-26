package tech.notifly.kmp.segment.model

/** A caller-owned, read-only attribute snapshot. Keys known to be unavailable are listed separately. */
class AttributeSnapshot(
    val values: Map<String, Any?>,
    val unavailableKeys: Set<String> = emptySet(),
)

/** The event name and parameters from the same event. A missing parameter map is represented by an empty map. */
class EventSnapshot(
    val name: String,
    val parameters: Map<String, Any?> = emptyMap(),
    val unavailableParameterKeys: Set<String> = emptySet(),
)

/** A nonnegative count for a KST calendar date in YYYY-MM-DD format. */
class EventCountRow(
    val kstDate: String,
    val count: Long,
)

/**
 * One event's aggregate rows, including the current occurrence exactly once if applicable.
 *
 * An all-time snapshot covers any date window. A limited snapshot uses inclusive KST dates for both coverage bounds.
 * A covered window can legitimately have no rows. Counts and their sums must fit in a JavaScript safe integer.
 */
class EventHistory(
    val rows: List<EventCountRow>,
    val allTime: Boolean = false,
    val coveredFrom: String? = null,
    val coveredThrough: String? = null,
)

/**
 * The inputs to one evaluation. A null attribute scope represents unavailable data.
 *
 * [evaluationAt] is an epoch millisecond timestamp. Pass stable snapshots for the duration of the call.
 */
class EvaluationContext(
    val evaluationAt: Long,
    val user: AttributeSnapshot? = null,
    val userMetadata: AttributeSnapshot? = null,
    val device: AttributeSnapshot? = null,
    val event: EventSnapshot? = null,
    val eventHistory: Map<String, EventHistory> = emptyMap(),
)

/**
 * Raw fields from a trigger or cancellation configuration.
 *
 * [nameConditions] and [parameterFilters] accept nested lists of condition maps. Null [nameConditions] permits
 * [legacyEventName]; an explicit malformed or empty name list is invalid.
 */
class EventPredicateInput(
    val legacyEventName: String? = null,
    val nameConditions: Any? = null,
    val parameterFilters: Any? = null,
)
