package tech.notifly.kmp.segment

import tech.notifly.kmp.segment.model.AttributeSnapshot
import tech.notifly.kmp.segment.model.EvaluationContext
import tech.notifly.kmp.segment.model.EventPredicateInput
import tech.notifly.kmp.segment.model.EventSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SegmentEvaluatorTest {
    @Test
    fun evaluateSegment_missingAndExplicitEmptyGroups_distinguishesInvalidFromUnrestricted() {
        val inputs = listOf(null, emptyMap(), mapOf("groups" to null), mapOf("groups" to emptyList<Any>()))
        val context = context()

        val actual = inputs.map { evaluateSegment(compilePopupSegment(it), context) }

        assertEquals(listOf(false, false, false, true), actual)
    }

    @Test
    fun evaluateSegment_invalidGroup_preservesOtherGroups() {
        val valid = group(condition())
        val invalidGroups =
            listOf(
                group(),
                group(null),
                group(condition(operator = "unknown")),
                group(condition(), operator = "OR"),
                group(condition() + ("attribute" to null)),
                group(condition() + ("unit" to "unknown")),
            )
        val context = context()

        val actual =
            invalidGroups.map { invalid ->
                listOf(listOf(invalid), listOf(invalid, valid), listOf(valid, invalid)).map {
                    evaluateSegment(compilePopupSegment(mapOf("groups" to it)), context)
                }
            }

        assertEquals(List(invalidGroups.size) { listOf(false, true, true) }, actual)
    }

    @Test
    fun evaluateSegment_nullOperators_combinesConditionsWithAndAndGroupsWithOr() {
        val groupA = group(condition(), condition(value = "silver"))
        val groupB = group(condition())
        val context = context()

        val actual =
            listOf(
                evaluateSegment(compilePopupSegment(segment(groupA)), context),
                evaluateSegment(compilePopupSegment(segment(groupA, groupB)), context),
                evaluateSegment(compilePopupSegment(segment(groupB) + ("group_operator" to "AND")), context),
            )

        assertEquals(listOf(false, true, false), actual)
    }

    @Test
    fun evaluateSegment_additionalPopupConditions_rejectsNonemptyTopLevelRestriction() {
        val wire = segment(group(condition()))
        val context = context()

        val actual =
            listOf(emptyList(), listOf(condition())).map {
                evaluateSegment(compilePopupSegment(wire + ("additional_conditions" to it)), context)
            }

        assertEquals(listOf(true, false), actual)
    }

    @Test
    fun evaluateSegment_invalidRenderedOperand_returnsFalseBeforeNegation() {
        val invalid = condition(operator = "<>") + ("evaluation_status" to "invalid")
        val prepared = compilePopupSegment(segment(group(invalid)))

        val actual = evaluateSegment(prepared, context(AttributeSnapshot(emptyMap())))

        assertFalse(actual)
    }

    @Test
    fun evaluateSegment_unavailableAttribute_cannotMatchExistenceOrNegation() {
        val snapshot = AttributeSnapshot(emptyMap(), unavailableKeys = setOf("tier"))
        val contexts = listOf(context(snapshot), context(null), context(AttributeSnapshot(emptyMap())))
        val operators = listOf("IS_NULL", "<>")

        val actual =
            contexts.map { context ->
                operators.map {
                    evaluateSegment(
                        compilePopupSegment(segment(group(condition(operator = it)))),
                        context,
                    )
                }
            }

        assertEquals(listOf(listOf(false, false), listOf(false, false), listOf(true, true)), actual)
    }

    @Test
    fun evaluateSegment_comparisonParameter_usesCurrentEventAndChecksTargetBeforeNegating() {
        val operands = mapOf("useEventParamsAsConditionValue" to true, "comparison_parameter" to "item_id")
        val testCases =
            listOf(
                Triple("=", mapOf("tier" to 30.5), mapOf("item_id" to "30.5")),
                Triple("<>", mapOf("tier" to 30), mapOf("item_id" to "30abc")),
                Triple("<>", emptyMap(), mapOf("item_id" to "30")),
                Triple("<>", mapOf("tier" to 30), emptyMap()),
                Triple("<>", mapOf("tier" to 30), mapOf("item_id" to null)),
            )

        val actual =
            testCases.map { (operator, properties, params) ->
                val wire = segment(group(condition(operator = operator, type = "INT") + operands))
                evaluateSegment(
                    compilePopupSegment(wire),
                    context(AttributeSnapshot(properties), EventSnapshot("buy", params)),
                )
            }

        assertEquals(listOf(true, false, true, false, false), actual)
    }

    @Test
    fun evaluateSegment_versionAndMetadata_usesOnlyTheDeclaredAttributeScope() {
        val context =
            EvaluationContext(
                evaluationAt = 1_795_050_000_000L,
                user = AttributeSnapshot(mapOf("app_version" to "1.10.0", "external_user_id" to "shadow")),
                userMetadata = AttributeSnapshot(mapOf("external_user_id" to "actual", "random_bucket_number" to 0)),
                device = AttributeSnapshot(mapOf("app_version" to "1.10.0")),
            )
        val conditions =
            listOf(
                condition(">", "1.9.0", attribute = "app_version", unit = "device"),
                condition(">", "1.9.0", attribute = "app_version"),
                condition(value = "actual", attribute = "external_user_id", unit = "user_metadata"),
                condition(">=", 0, attribute = "random_bucket_number", unit = "user_metadata", type = "INT"),
                condition("IS_NULL", null, attribute = "unknown", unit = "device"),
            )

        val actual = conditions.map { evaluateSegment(compilePopupSegment(segment(group(it))), context) }

        assertEquals(listOf(true, false, true, true, false), actual)
    }

    @Test
    fun evaluateSegment_interleavedContexts_doesNotReusePreviousResult() {
        val prepared = compilePopupSegment(segment(group(condition())))
        val gold = context()
        val silver = context(AttributeSnapshot(mapOf("tier" to "silver")))

        val actual = listOf(gold, silver, gold).map { evaluateSegment(prepared, it) }

        assertEquals(listOf(true, false, true), actual)
    }

    @Test
    fun compilePopupSegment_mutableInput_makesIndependentPreparedCondition() {
        val condition = condition().toMutableMap()
        val wire = segment(group(condition))
        val prepared = compilePopupSegment(wire)
        val properties = mutableMapOf("tier" to "gold")
        val context = context(AttributeSnapshot(properties))

        condition["value"] = "silver"
        val actual = listOf(prepared, compilePopupSegment(wire)).map { evaluateSegment(it, context) }

        assertEquals(listOf(true, false), actual)
        assertEquals(mapOf("tier" to "gold"), properties)
    }

    @Test
    fun matchesEvent_legacyNameAndExplicitV2Configuration_onlyFallsBackWhenAbsent() {
        val cases = listOf(null, emptyList<Any>(), "invalid", listOf(listOf(name("=", "purchase"))))
        val event = EventSnapshot("purchase")

        val actual = cases.map { matchesEvent(compileEventPredicates(EventPredicateInput("purchase", it)), event) }

        assertEquals(listOf(true, false, false, true), actual)
    }

    @Test
    fun matchesEvent_nameConditions_useCaseSensitiveOperatorsAndOrGroups() {
        val event = EventSnapshot("purchase_paid")
        val groupA = listOf(name("starts_with", "purchase"), name("ends_with", "_failed"))
        val groupB = listOf(name("matches_regex", "paid"), name("!=", "Purchase_paid"))
        val invalidGroup = listOf(name("does_not_match_regex", "["))
        val inputs = listOf(listOf(groupA), listOf(groupA, groupB), listOf(invalidGroup, groupB), listOf(invalidGroup))

        val actual =
            inputs.map {
                matchesEvent(
                    compileEventPredicates(EventPredicateInput(nameConditions = it)),
                    event,
                )
            }

        assertEquals(listOf(false, true, true, false), actual)
    }

    @Test
    fun matchesEvent_parameterFilterShape_distinguishesUnrestrictedAndInvalid() {
        val cases = listOf(null, emptyList<Any>(), listOf(emptyList<Any>()), "invalid")
        val event = EventSnapshot("purchase")

        val actual =
            cases.map {
                matchesEvent(compileEventPredicates(EventPredicateInput("purchase", parameterFilters = it)), event)
            }

        assertEquals(listOf(true, true, false, false), actual)
    }

    @Test
    fun matchesEvent_nameAndFilter_combinesResultsAndIsolatesInvalidGroups() {
        val filters = listOf(listOf(filter("unknown", 10_000)), listOf(filter(">=", 10_000)))
        val prepared = compileEventPredicates(EventPredicateInput("purchase", parameterFilters = filters))
        val events =
            listOf(
                EventSnapshot("purchase", mapOf("amount" to "15000")),
                EventSnapshot("refund", mapOf("amount" to "15000")),
                EventSnapshot("purchase", mapOf("amount" to "bad")),
            )

        val actual = events.map { matchesEvent(prepared, it) }

        assertEquals(listOf(true, false, false), actual)
    }

    @Test
    fun matchesEvent_missingEventParameters_stillEvaluatesExistenceAndNegation() {
        val event = EventSnapshot("purchase")
        val cases = listOf("IS_NULL", "<>", "=")

        val actual =
            cases.map { operator ->
                val wire =
                    mapOf("key" to "coupon", "operator" to operator, "value_type" to "TEXT", "value" to "WELCOME")
                val prepared =
                    compileEventPredicates(EventPredicateInput("purchase", parameterFilters = listOf(listOf(wire))))
                matchesEvent(prepared, event)
            }

        assertEquals(listOf(true, true, false), actual)
    }

    @Test
    fun matchesEvent_emptyName_returnsFalseForNegation() {
        val event = EventSnapshot("")
        val conditions = listOf(listOf(name("!=", "purchase")))
        val prepared = compileEventPredicates(EventPredicateInput(nameConditions = conditions))

        val actual = matchesEvent(prepared, event)

        assertFalse(actual)
    }

    @Test
    fun evaluateSegment_emptyEventName_cannotSupplyAComparisonOperand() {
        val event = EventSnapshot("", mapOf("item_id" to "gold"))
        val wire = condition() + mapOf("useEventParamsAsConditionValue" to true, "comparison_parameter" to "item_id")
        val prepared = compilePopupSegment(segment(group(wire)))

        val actual = evaluateSegment(prepared, context(event = event))

        assertFalse(actual)
    }

    private fun context(
        user: AttributeSnapshot? = AttributeSnapshot(mapOf("tier" to "gold")),
        event: EventSnapshot? = null,
    ) = EvaluationContext(evaluationAt = 1_795_050_000_000L, user = user, event = event)

    private fun segment(vararg groups: Map<String, Any?>): Map<String, Any?> =
        mapOf("groups" to groups.toList(), "group_operator" to null)

    private fun group(
        vararg conditions: Map<String, Any?>?,
        operator: String? = null,
    ): Map<String, Any?> = mapOf("conditions" to conditions.toList(), "condition_operator" to operator)

    private fun condition(
        operator: String = "=",
        value: Any? = "gold",
        attribute: String = "tier",
        unit: String = "user",
        type: String = "TEXT",
    ): Map<String, Any?> =
        mapOf("unit" to unit, "attribute" to attribute, "valueType" to type, "operator" to operator, "value" to value)

    private fun name(
        operator: String,
        operand: String,
    ): Map<String, Any?> = mapOf("type" to "event_name", "operator" to operator, "operand" to operand)

    private fun filter(
        operator: String,
        value: Any,
    ): Map<String, Any?> = mapOf("key" to "amount", "operator" to operator, "value_type" to "INT", "value" to value)
}
