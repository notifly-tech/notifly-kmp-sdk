package tech.notifly.kmp.segment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComparisonTest {
    @Test
    fun match_textEquality_acceptsOnlyIdenticalText() {
        val comparison = compileComparison("=", "TEXT", "gold")

        val actual = listOf("gold", "silver").map { comparison?.matches(it) }

        assertEquals(listOf(true, false), actual)
    }

    @Test
    fun match_missingSourceWithValidTarget_matchesOnlyNegation() {
        val cases = listOf("=" to false, ">" to false, "<>" to true, "does_not_contain" to true)

        cases.forEach { (operator, expected) ->
            val actual = match(operator, "TEXT", null, "gold")

            assertEquals(expected, actual, operator)
        }
    }

    @Test
    fun match_existenceWithoutTypeOrTarget_distinguishesNullFromEmpty() {
        val values = listOf(null, "", 0, false, emptyList<Any>(), emptyMap<String, Any>())

        val actual = values.map { match("IS_NULL", null, it, null) to match("IS_NOT_NULL", null, it, null) }

        assertEquals(listOf(true to false) + List(5) { false to true }, actual)
    }

    @Test
    fun match_badTarget_returnsFalseEvenForNegation() {
        val cases =
            listOf(
                Triple("INT", "30abc", 30),
                Triple("BOOL", "true", true),
                Triple("TEXT", 30, "30"),
                Triple("INT", "1" + "0".repeat(309), 30),
                Triple("INT", null, 30),
            )

        cases.forEach { (type, target, source) ->
            val actual = match("<>", type, source, target)

            assertFalse(actual, type)
        }
    }

    @Test
    fun match_numericSource_preservesTheFullFiniteNumber() {
        val cases =
            listOf(
                Triple("030", 30, true),
                Triple(30.0, 30, true),
                Triple("30.5", 30, false),
                Triple(true, 30, false),
                Triple(listOf(30), 30, false),
                Triple("", 0, false),
                Triple(-30.5, "-030.5", true),
            )

        cases.forEach { (source, target, expected) ->
            val actual = match("=", "INT", source, target)

            assertEquals(expected, actual, "Source: $source, target: $target")
        }
    }

    @Test
    fun match_numericOrdering_rejectsNonDecimalOrNonFiniteSources() {
        val invalid =
            listOf("30abc", " 30", "30 ", "30\n", "+30", "3e1", "3,000", "30.", ".5", Double.POSITIVE_INFINITY, null)

        invalid.forEach { source ->
            val actual = match(">", "INT", source, 0)

            assertFalse(actual, "Source: $source")
        }
    }

    @Test
    fun match_numericOrdering_preservesFractionsAndWideIntegers() {
        val cases = listOf(30.5, 3_000_000_000L)

        cases.forEach { source ->
            val actual = match(">", "INT", source, 30)

            assertTrue(actual, "Source: $source")
        }
    }

    @Test
    fun match_failedSourceConversion_canMatchNegation() {
        val source = "30abc"

        val actual = match("=", "INT", source, 30) to match("<>", "INT", source, 30)

        assertEquals(false to true, actual)
    }

    @Test
    fun match_orderingAtEqualBoundary_matchesOnlyInclusiveOperators() {
        val cases = listOf(">" to false, ">=" to true, "<" to false, "<=" to true)

        cases.forEach { (operator, expected) ->
            val actual = match(operator, "INT", 30, 30)

            assertEquals(expected, actual, operator)
        }
    }

    @Test
    fun match_numericEquality_preservesBinary64Boundaries() {
        val largeInteger = 9_007_199_254_740_992.0
        val cases =
            listOf(
                Triple(largeInteger, largeInteger, true),
                Triple(largeInteger, 9_007_199_254_740_991.0, false),
                Triple(-0.0, 0, true),
            )

        cases.forEach { (source, target, expected) ->
            val actual = match("=", "INT", source, target)

            assertEquals(expected, actual, "$source = $target")
        }
    }

    @Test
    fun match_booleanSource_acceptsOnlySpecifiedValues() {
        val truthy = listOf(true, "TrUe", 1, "1")
        val falsy = listOf(false, "FaLsE", 0, "0")
        val invalid = listOf(null, "T", "yes", " true", 2, -1, 0.5)

        val actual =
            listOf(
                truthy.all { match("=", "BOOL", it, true) },
                falsy.all { match("=", "BOOL", it, false) },
                invalid.none { match("=", "BOOL", it, false) || match("=", "BOOL", it, true) },
            )

        assertEquals(listOf(true, true, true), actual)
    }

    @Test
    fun match_booleanOrdering_rejectsTheOperator() {
        val comparison = compileComparison(">", "BOOL", false)

        val actual = comparison?.matches(true) ?: false

        assertFalse(actual)
    }

    @Test
    fun match_textSource_usesEcmaNumberAndBooleanRepresentations() {
        val cases =
            listOf(
                30.0 to "30",
                30.5 to "30.5",
                true to "true",
                false to "false",
                -0.0 to "0",
                1e21 to "1e+21",
                1e-7 to "1e-7",
                1e-6 to "0.000001",
                1e23 to "1e+23",
                Double.MIN_VALUE to "5e-324",
                Double.MAX_VALUE to "1.7976931348623157e+308",
                1_000_000_000_000_000_128.0 to "1000000000000000100",
            )

        cases.forEach { (source, expected) ->
            val actual = match("=", "TEXT", source, expected)

            assertTrue(actual, "Expected text: $expected")
        }
    }

    @Test
    fun match_textEquality_preservesCaseWhitespaceAndUnicodeRepresentation() {
        val cases =
            listOf(
                Triple("gold ", "gold", false),
                Triple("Gold", "gold", false),
                Triple("\u00e9", "e\u0301", false),
                Triple("", "", true),
            )

        cases.forEach { (source, target, expected) ->
            val actual = match("=", "TEXT", source, target)

            assertEquals(expected, actual, "Source: $source, target: $target")
        }
    }

    @Test
    fun match_textOrdering_comparesUtf16UnitsLexically() {
        val cases =
            listOf(
                ComparisonCase("<", "\uD83D\uDE00", "\uE000", true),
                ComparisonCase(">", 10, "9", false),
            )

        cases.forEach { (operator, source, target, expected) ->
            val actual = match(operator, "TEXT", source, target)

            assertEquals(expected, actual, "$source $operator $target")
        }
    }

    @Test
    fun match_textEquality_rejectsArrayAndObjectSources() {
        val cases = listOf(listOf("a"), emptyMap<String, Any>())

        cases.forEach { source ->
            val actual = match("=", "TEXT", source, "a") to match("<>", "TEXT", source, "a")

            assertEquals(false to true, actual, "Source: $source")
        }
    }

    @Test
    fun match_excludedArray_matchesTheComplementOfInclusion() {
        val cases = listOf(null to true, "vip" to true, emptyList<String>() to true, listOf("vip") to false)

        cases.forEach { (source, expected) ->
            val actual = match("NOT_INCLUDE", "TEXT", source, "vip")

            assertEquals(expected, actual, "Source: $source")
        }
    }

    @Test
    fun match_includedArray_convertsElementsToTheConditionType() {
        val text = listOf(1, 2)
        val numeric = listOf("1.0")
        val boolean = listOf("1", 0)

        val actual =
            listOf(
                match("@>", "TEXT", text, "1"),
                match("@>", "INT", numeric, 1),
                match("@>", "BOOL", boolean, true),
                match("NOT_INCLUDE", "BOOL", boolean, true),
            )

        assertEquals(listOf(true, true, true, false), actual)
    }

    @Test
    fun match_includedArray_ignoresUnconvertibleElements() {
        val invalidElements = listOf(null, listOf("x"), mapOf("a" to "x"))
        val sources = listOf(invalidElements, invalidElements + "x")

        val actual = sources.map { match("@>", "TEXT", it, "x") }

        assertEquals(listOf(false, true), actual)
    }

    @Test
    fun match_arraySubstring_matchesOnlyNonemptySubstringsOfConvertedElements() {
        val source = listOf(10, null, "gold")
        val cases = listOf("1" to true, "ol" to true, "" to false)

        cases.forEach { (target, expected) ->
            val actual = match("CONTAINS_ANY_OF", "TEXT", source, target)

            assertEquals(expected, actual, "Target: $target")
        }
    }

    @Test
    fun match_stringOperators_complementValidConditions() {
        val cases =
            listOf(
                Triple("starts_with", "does_not_start_with", "order_"),
                Triple("ends_with", "does_not_end_with", "_v2"),
                Triple("contains", "does_not_contain", "paid"),
            )

        cases.forEach { (positive, negative, target) ->
            val actual =
                listOf(
                    match(positive, "TEXT", "order_paid_v2", target),
                    match(negative, "TEXT", "order_paid_v2", target),
                    match(negative, "TEXT", null, target),
                    match(negative, "TEXT", listOf(target), target),
                )

            assertEquals(listOf(true, false, true, true), actual, positive)
        }
    }

    @Test
    fun match_stringOperatorsWithEmptyTarget_rejectPositiveAndNegativeConditions() {
        val operators =
            listOf(
                "starts_with",
                "does_not_start_with",
                "ends_with",
                "does_not_end_with",
                "contains",
                "does_not_contain",
            )

        operators.forEach { operator ->
            val actual = match(operator, "TEXT", "order_paid_v2", "")

            assertFalse(actual, operator)
        }
    }

    @Test
    fun match_invalidRegex_returnsFalseBeforeNegating() {
        val invalid = listOf("[", "(?<=a)b", "(?=a)", "(a)\\1", "(?i:a)", "a{1001}", "a++", "[z-a]")

        invalid.forEach { pattern ->
            val actual =
                match("matches_regex", "TEXT", "order_paid_v2", pattern) to
                    match("does_not_match_regex", "TEXT", "order_paid_v2", pattern)

            assertEquals(false to false, actual, pattern)
        }
    }

    @Test
    fun match_invalidDynamicRegex_returnsFalseBeforeNegating() {
        val dynamic = compileComparison("does_not_match_regex", "TEXT", null, dynamicTarget = true)

        val actual = dynamic?.matches("order_paid_v2", "[")

        assertEquals(false, actual)
    }

    @Test
    fun match_regexOperand_matchesSubstringsAndHonorsAnchors() {
        val source = "order_paid_v2"

        val actual =
            listOf(
                match("matches_regex", "TEXT", source, "paid"),
                match("matches_regex", "TEXT", source, "^paid$"),
                match("does_not_match_regex", "TEXT", null, "paid"),
            )

        assertEquals(listOf(true, false, true), actual)
    }

    @Test
    fun match_regexSyntax_usesFlagFreeUtf16AndBoundedRepetition() {
        val cases =
            listOf(
                Triple("a\n", "a$", false),
                Triple("\uD83D\uDE00", "^.$", false),
                Triple("\u0661", "^\\d$", false),
                Triple("\u00a0", "^\\s$", true),
                Triple("order_paid12", "^order_(?:paid|sent)[0-9]{2,3}$", true),
                Triple("order_sent9", "^order_(?:paid|sent)[0-9]{2,3}$", false),
                Triple("fooaaa", "(?:a+)+$", true),
                Triple("b", "a*b", true),
                Triple("xx", "[^a-z]", false),
                Triple("xx_", "[^a-z]", true),
            )

        val actual = cases.map { (source, pattern) -> match("matches_regex", "TEXT", source, pattern) }

        assertEquals(cases.map { it.third }, actual)
    }

    @Test
    fun match_regexCharacterClasses_preservesOverlappingAndComplementaryRanges() {
        val cases =
            listOf(
                Triple("xyz8 ", "^[a-hd-z0-9\\s]+$", true),
                Triple("4a", "^[a-f\\D]+$", false),
                Triple("_!", "^[^a-z\\d]+$", true),
                Triple("a", "^[^a-z\\d]+$", false),
                Triple("b".repeat(640), "^[" + "a".repeat(1_500) + "b]{640}$", true),
            )

        val actual = cases.map { (source, pattern) -> match("matches_regex", "TEXT", source, pattern) }

        assertEquals(cases.map { it.third }, actual)
    }

    @Test
    fun match_deviceVersion_usesVersionPrecedenceAndTextFallback() {
        val cases =
            listOf(
                VersionCase(">", "1.10.0", "1.9.0", true),
                VersionCase("=", "1.0", "1.0.0", true),
                VersionCase("=", "v1.0.0+45", "1.0.0", true),
                VersionCase(">", "1.0.0.1", "1.0.0", true),
                VersionCase(">=", "2.0.0-beta.1", "2.0.0", false),
                VersionCase(">", "1.2.3-beta.10", "1.2.3-beta.2", true),
                VersionCase("=", "1.10.0 (45)", "1.10.0 (45)", true),
                VersionCase("<>", "1.10.0 (45)", "1.10.0", true),
                VersionCase(">", "1.10.0 (45)", "1.9.0", false),
                VersionCase("starts_with", "1.10.0 (45)", "1.10", true),
            )

        cases.forEach { (operator, source, target, expected) ->
            val actual = match(operator, "TEXT", source, target, version = true)

            assertEquals(expected, actual, "$source $operator $target")
        }
    }

    @Test
    fun match_deviceVersionWithManyIdentifiers_usesNumericPrecedenceWithoutRecursion() {
        val prefix = "1.2.3-x" + ".x".repeat(4_096)

        val actual = match(">", "TEXT", "$prefix.10", "$prefix.2", version = true)

        assertTrue(actual)
    }

    private data class ComparisonCase(
        val operator: String,
        val source: Any?,
        val target: Any?,
        val expected: Boolean,
    )

    private data class VersionCase(
        val operator: String,
        val source: String,
        val target: String,
        val expected: Boolean,
    )

    private fun match(
        operator: String,
        type: String?,
        source: Any?,
        target: Any?,
        version: Boolean = false,
    ): Boolean = compileComparison(operator, type, target, version = version)?.matches(source) ?: false
}
