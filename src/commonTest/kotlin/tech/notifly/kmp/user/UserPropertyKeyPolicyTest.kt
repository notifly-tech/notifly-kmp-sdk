package tech.notifly.kmp.user

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserPropertyKeyPolicyTest {
    @Test
    fun warningMessage_emptyKeys_returnsNull() {
        assertNull(UserPropertyKeyPolicy.warningMessage(emptyArray()))
    }

    @Test
    fun warningMessage_asciiKeys_returnsNull() {
        val keys = arrayOf("user_name", "User123", "_", "123", "1st_purchase")

        assertNull(UserPropertyKeyPolicy.warningMessage(keys))
    }

    @Test
    fun warningMessage_exactReservedKeys_returnsNull() {
        val keys =
            arrayOf(
                "\$email",
                "\$phone_number",
                "\$timezone",
                "\$line_user_id",
                "\$appsflyer_id",
                "\$web_push_consent",
                "\$web_push_consented_at",
            )

        assertNull(UserPropertyKeyPolicy.warningMessage(keys))
    }

    @Test
    fun warningMessage_invalidKeys_returnsWarning() {
        val keys =
            listOf(
                "",
                "first.name",
                "full name",
                "사용자명",
                "\$custom",
                "\$EMAIL",
                "\$WEB_PUSH_CONSENT",
                "a^b",
                "a-b",
                "a/b",
                "😀",
                "é",
                "Ａ",
                "name\n",
                "name\r\n",
                "name\t",
                "name\u00a0",
                "name\u0000",
            )

        keys.forEach { key ->
            assertNotNull(UserPropertyKeyPolicy.warningMessage(arrayOf(key)), "Key: $key")
        }
    }

    @Test
    fun warningMessage_mixedKeys_listsOnlySortedInvalidKeys() {
        val keys = arrayOf("사용자명", "valid_key", "\$email", "first.name")

        val warning = UserPropertyKeyPolicy.warningMessage(keys)

        assertEquals(
            "Invalid custom user property key(s): [\"first.name\",\"사용자명\"]. " +
                "Only English letters (A-Z, a-z), digits (0-9), and underscores (_) are supported. " +
                "This warning does not block the operation.",
            warning,
        )
    }

    @Test
    fun warningMessage_controlCharacters_escapesKeyList() {
        val warning = UserPropertyKeyPolicy.warningMessage(arrayOf("name\n\"\t\\\u0000"))

        assertNotNull(warning)
        assertTrue(warning.contains("[\"name\\n\\\"\\t\\\\\\u0000\"]"))
        assertTrue(warning.none { it == '\n' || it == '\t' || it == '\u0000' })
    }

    @Test
    fun warningMessage_unsortedInput_preservesInput() {
        val keys = arrayOf("z.key", "a.key", "valid")
        val original = keys.copyOf()

        UserPropertyKeyPolicy.warningMessage(keys)

        assertContentEquals(original, keys)
    }

    @Test
    fun warningMessage_repeatedInvalidKey_warnsEveryTime() {
        val keys = arrayOf("old.key")

        val first = UserPropertyKeyPolicy.warningMessage(keys)
        val second = UserPropertyKeyPolicy.warningMessage(keys)

        assertNotNull(first)
        assertEquals(first, second)
    }
}
