package tech.notifly.kmp.identity

import kotlin.test.Test
import kotlin.test.assertEquals

class UserIdTransitionPolicyTest {
    @Test
    fun evaluatesEverySupportedIdentityTransition() {
        val rows =
            listOf(
                Row(null, "A", true, true, true, false),
                Row("A", "B", true, true, false, false),
                Row("A", null, true, true, false, true),
                Row("A", "A", false, false, false, false),
                Row(null, null, false, false, false, true),
            )

        rows.forEach { row ->
            val actual = UserIdTransitionPolicy.evaluate(row.previousUserId, row.newUserId)

            assertEquals(row.changed, actual.changed, row.description("changed"))
            assertEquals(row.shouldSync, actual.shouldSync, row.description("shouldSync"))
            assertEquals(row.shouldMerge, actual.shouldMerge, row.description("shouldMerge"))
            assertEquals(row.shouldClear, actual.shouldClear, row.description("shouldClear"))
        }
    }

    private data class Row(
        val previousUserId: String?,
        val newUserId: String?,
        val changed: Boolean,
        val shouldSync: Boolean,
        val shouldMerge: Boolean,
        val shouldClear: Boolean,
    ) {
        fun description(property: String): String =
            "$property for transition ${previousUserId ?: "null"} -> ${newUserId ?: "null"}"
    }
}
