package tech.notifly.kmp.segment

import tech.notifly.kmp.segment.model.EvaluationContext
import tech.notifly.kmp.segment.model.EventCountRow
import tech.notifly.kmp.segment.model.EventHistory
import tech.notifly.kmp.segment.model.EventSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventCountConditionTest {
    @Test
    fun evaluateSegment_allTimeCount_usesProvidedSnapshotIncludingTheCurrentEvent() {
        val history = EventHistory(listOf(EventCountRow("2026-11-19", 2)), allTime = true)
        val prepared = condition("count X", 2)
        val context = context(history)

        val actual = evaluateSegment(prepared, context)

        assertTrue(actual)
    }

    @Test
    fun evaluateSegment_recentCount_includesBothKstDateBoundaries() {
        val history =
            EventHistory(
                listOf(
                    EventCountRow("2026-11-11", 1),
                    EventCountRow("2026-11-12", 1),
                    EventCountRow("2026-11-19", 1),
                    EventCountRow("2026-11-20", 1),
                ),
                allTime = true,
            )

        val actual = evaluateSegment(condition("count X in Y days", 2, days = 7), context(history))

        assertTrue(actual)
    }

    @Test
    fun evaluateSegment_midnightAndZeroDays_usesExplicitEvaluationTime() {
        val history =
            EventHistory(
                listOf(EventCountRow("2026-11-18", 1), EventCountRow("2026-11-19", 1)),
                coveredFrom = "2026-11-18",
                coveredThrough = "2026-11-20",
            )
        val prepared = condition("count X in Y days", 1, days = 0)
        val epochs = listOf(1_795_017_600_000L, 1_795_100_340_000L, 1_795_100_460_000L)

        val actual = epochs.map { evaluateSegment(prepared, context(history, it)) }

        assertEquals(listOf(true, true, false), actual)
    }

    @Test
    fun evaluateSegment_historicalSeoulOffset_usesDaylightSavingCalendarDate() {
        val history = EventHistory(listOf(EventCountRow("1988-07-02", 1)), allTime = true)
        val prepared = condition("count X in Y days", 1, days = 0)

        val actual = evaluateSegment(prepared, context(history, evaluationAt = 583_770_600_000L))

        assertTrue(actual)
    }

    @Test
    fun evaluateSegment_incompleteCountCoverage_distinguishesUnavailableFromZero() {
        val covered = EventHistory(emptyList(), coveredFrom = "2026-11-12", coveredThrough = "2026-11-19")
        val truncated = EventHistory(emptyList(), coveredFrom = "2026-11-13", coveredThrough = "2026-11-19")
        val prepared = condition("count X in Y days", 0, days = 7)

        val actual =
            listOf(
                evaluateSegment(prepared, context(covered)),
                evaluateSegment(prepared, context(truncated)),
                evaluateSegment(prepared, context(null)),
                evaluateSegment(condition("count X", 0), context(covered)),
            )

        assertEquals(listOf(true, false, false, false), actual)
    }

    @Test
    fun evaluateSegment_invalidEventCondition_rejectsBadCountWindowOperatorAndFilter() {
        val base = mapOf("unit" to "event", "event" to "purchase", "event_condition_type" to "count X in Y days")
        val valid = mapOf("operator" to ">=", "value" to 0, "secondary_value" to 0)
        val invalid =
            listOf(
                "value" to -1,
                "value" to 1.5,
                "secondary_value" to -1,
                "secondary_value" to 1.5,
                "secondary_value" to null,
                "operator" to "<>",
                "comparison_parameter_field_name" to "amount",
                "comparison_operator" to ">=",
                "useEventParamsAsConditionValue" to "true",
            )
        val context = context(EventHistory(listOf(EventCountRow("2026-11-19", 100)), allTime = true))

        val actual = invalid.map { evaluateSegment(compilePopupSegment(wire(base + valid + it)), context) }

        assertTrue(actual.none { it })
    }

    @Test
    fun evaluateSegment_largeCounts_sumsBeyondTheIntRange() {
        val history =
            EventHistory(
                listOf(EventCountRow("2026-11-18", 2_000_000_000L), EventCountRow("2026-11-19", 1_000_000_000L)),
                allTime = true,
            )

        val actual = evaluateSegment(condition("count X", 3_000_000_000L), context(history))

        assertTrue(actual)
    }

    @Test
    fun evaluateSegment_unrepresentableOrMalformedCounts_returnsFalse() {
        val malformed =
            listOf(
                EventHistory(listOf(EventCountRow("2026-11-19", -1)), allTime = true),
                EventHistory(listOf(EventCountRow("2026-02-30", 1)), allTime = true),
                EventHistory(
                    listOf(EventCountRow("2026-11-19", 9_007_199_254_740_991L), EventCountRow("2026-11-19", 1)),
                    allTime = true,
                ),
            )
        val prepared = condition("count X", 0, operator = ">=")

        val actual = malformed.map { evaluateSegment(prepared, context(it)) }

        assertEquals(listOf(false, false, false), actual)
    }

    @Test
    fun evaluateSegment_unsafeCountThreshold_rejectsACoveredHistory() {
        val history = EventHistory(listOf(EventCountRow("2026-11-19", 1)), allTime = true)
        val prepared = condition("count X", 9_007_199_254_740_992L, operator = "<=")

        val actual = evaluateSegment(prepared, context(history))

        assertFalse(actual)
    }

    private fun condition(
        type: String,
        count: Number,
        days: Int? = null,
        operator: String = "=",
    ) = compilePopupSegment(
        wire(
            mapOf(
                "unit" to "event",
                "event" to "purchase",
                "event_condition_type" to type,
                "secondary_value" to days,
                "value" to count,
                "operator" to operator,
            ),
        ),
    )

    private fun context(
        history: EventHistory?,
        evaluationAt: Long = 1_795_050_000_000L,
    ) = EvaluationContext(
        evaluationAt = evaluationAt,
        event = EventSnapshot("purchase"),
        eventHistory =
            mapOf("login" to EventHistory(listOf(EventCountRow("2026-11-19", 1)), allTime = true)) +
                (history?.let { mapOf("purchase" to it) } ?: emptyMap()),
    )

    private fun wire(condition: Map<String, Any?>): Map<String, Any?> =
        mapOf("groups" to listOf(mapOf("conditions" to listOf(condition))))
}
