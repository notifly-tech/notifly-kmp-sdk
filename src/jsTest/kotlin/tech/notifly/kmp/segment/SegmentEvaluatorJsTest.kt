package tech.notifly.kmp.segment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SegmentEvaluatorJsTest {
    @Test
    fun evaluateSegmentJs_objectProperties_ignoresInheritedKeysAndTreatsUndefinedAsMissing() {
        val wire = js("({groups:[{conditions:[{unit:'user', attribute:'tier', operator:'IS_NULL'}]}]})")
        val prepared = compilePopupSegmentJs(wire)
        val data = js("[{}, {tier:undefined}, {tier:null}, Object.create({tier:'gold'}), {tier:''}]")
        val context = js("({evaluationAt:1795050000000, user:{}})")

        val actual =
            (0..4).map { index ->
                context.user.values = data[index]
                evaluateSegmentJs(prepared, createEvaluationContextJs(context))
            }

        assertEquals(listOf(true, true, true, true, false), actual)
    }

    @Test
    fun evaluateSegmentJs_eventCounts_acceptsTheExplicitKstWindowAndWideIntegerCounts() {
        val wire =
            js(
                "({groups:[{conditions:[{unit:'event', event:'purchase', event_condition_type:'count X in Y days'," +
                    "operator:'=', value:3000000000, secondary_value:0}]}]})",
            )
        val input =
            js(
                "({evaluationAt:1795017600000,eventHistory:{purchase:{coveredFrom:'2026-11-19'," +
                    "coveredThrough:'2026-11-19', rows:[{kstDate:'2026-11-19',count:3000000000}]}}})",
            )
        val prepared = compilePopupSegmentJs(wire)

        val actual = evaluateSegmentJs(prepared, createEvaluationContextJs(input))

        assertTrue(actual)
    }

    @Test
    fun evaluateSegmentJs_invalidSiblingCondition_doesNotDiscardTheMatchingGroup() {
        val invalid = js("({unit:'user',attribute:'tier',operator:'=',valueType:'TEXT'})")
        var getterCalls = 0
        val getter: () -> String = {
            getterCalls++
            "gold"
        }
        val descriptor = js("({enumerable:true})")
        descriptor.get = getter
        js("Object.defineProperty")(invalid, "value", descriptor)
        val valid = js("({unit:'user',attribute:'tier',operator:'=',valueType:'TEXT',value:'gold'})")
        val wire = js("({groups:[{},{}]})")
        wire.groups[0].conditions = arrayOf(invalid)
        wire.groups[1].conditions = arrayOf(valid)
        val context = createEvaluationContextJs(js("({evaluationAt:1795050000000,user:{values:{tier:'gold'}}})"))

        val actual = evaluateSegmentJs(compilePopupSegmentJs(wire), context)

        assertTrue(actual)
        assertEquals(0, getterCalls)
    }

    @Test
    fun matchesEventJs_optionalParameters_matchesNullFilterWhenAbsent() {
        val prepared = couponNullFilter()
        val event = js("({name:'refund'})")

        val actual = matchesEventJs(prepared, event)

        assertTrue(actual)
    }

    @Test
    fun matchesEventJs_malformedParameters_rejectsTheEvent() {
        val prepared = couponNullFilter()
        val events = js("[{name:'refund',parameters:123},{name:'refund',parameters:new Date()}]")

        val actual = (0..1).map { matchesEventJs(prepared, events[it]) }

        assertEquals(listOf(false, false), actual)
    }

    @Test
    fun matchesEventJs_mismatchedName_rejectsTheEvent() {
        val prepared = couponNullFilter()
        val event = js("({name:'purchase'})")

        val actual = matchesEventJs(prepared, event)

        assertFalse(actual)
    }

    @Test
    fun compileEventPredicatesJs_inheritedLegacyName_doesNotConfigureATrigger() {
        val wire = js("Object.create({legacyEventName:'refund'})")
        val event = js("({name:'refund'})")

        val actual = matchesEventJs(compileEventPredicatesJs(wire), event)

        assertFalse(actual)
    }

    @Test
    fun evaluateSegmentJs_nonEnumerableOwnData_isStillPresent() {
        val wire = js("({groups:[{conditions:[{unit:'user', attribute:'tier', operator:'IS_NULL'}]}]})")
        val input = js("({evaluationAt:1795050000000,user:{values:{}}})")
        js("Object.defineProperty")(input.user.values, "tier", js("({value:'gold'})"))

        val actual = evaluateSegmentJs(compilePopupSegmentJs(wire), createEvaluationContextJs(input))

        assertFalse(actual)
    }

    @Test
    fun evaluateSegmentJs_unreadableArrayElement_cannotMatchNegatedInclusion() {
        val wire =
            js(
                "({groups:[{conditions:[{unit:'user',attribute:'tags',operator:'NOT_INCLUDE',valueType:'TEXT',value:'vip'}]}]})",
            )
        val input = js("({evaluationAt:1795050000000,user:{values:{tags:[]}}})")
        var calls = 0
        val getter: () -> String = {
            calls++
            "vip"
        }
        val descriptor = js("({enumerable:true})")
        descriptor.get = getter
        js("Object.defineProperty")(input.user.values.tags, "0", descriptor)

        val actual = evaluateSegmentJs(compilePopupSegmentJs(wire), createEvaluationContextJs(input))

        assertFalse(actual)
        assertEquals(0, calls)
    }

    @Test
    fun evaluateSegmentJs_nonRecordAttributes_doesNotMatchMissingProperties() {
        val wire = js("({groups:[{conditions:[{unit:'user', attribute:'tier', operator:'IS_NULL'}]}]})")
        val input = js("({evaluationAt:1795050000000,user:{values:new Map([['tier','gold']])}})")
        val prepared = compilePopupSegmentJs(wire)

        val actual = evaluateSegmentJs(prepared, createEvaluationContextJs(input))

        assertFalse(actual)
    }

    @Test
    fun createEvaluationContextJs_invalidTimestamp_cannotEnlargeTargeting() {
        val wire = js("({groups:[]})")
        val prepared = compilePopupSegmentJs(wire)
        val inputs = js("[{}, {evaluationAt:true}, {evaluationAt:Infinity}, {evaluationAt:1000.5}]")

        val actual = (0..3).map { evaluateSegmentJs(prepared, createEvaluationContextJs(inputs[it])) }

        assertEquals(List(4) { false }, actual)
    }

    private fun couponNullFilter() =
        compileEventPredicatesJs(
            js("({legacyEventName:'refund', parameterFilters:[[{key:'coupon', operator:'IS_NULL'}]]})"),
        )
}
