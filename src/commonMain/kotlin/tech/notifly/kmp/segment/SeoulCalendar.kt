package tech.notifly.kmp.segment

private const val MILLIS_PER_DAY = 86_400_000L
private const val INITIAL_OFFSET_SECONDS = 30_472

/** Deterministic Gregorian dates using the Asia/Seoul transitions from IANA tzdb 2026e. */
internal object SeoulCalendar {
    private class Transition(
        val epochSeconds: Long,
        val offsetSeconds: Int,
    )

    private val transitions =
        listOf(
            Transition(-1_948_782_472L, 30_600),
            Transition(-1_830_414_600L, 32_400),
            Transition(-681_210_000L, 36_000),
            Transition(-672_228_000L, 32_400),
            Transition(-654_771_600L, 36_000),
            Transition(-640_864_800L, 32_400),
            Transition(-623_408_400L, 36_000),
            Transition(-609_415_200L, 32_400),
            Transition(-588_848_400L, 36_000),
            Transition(-577_965_600L, 32_400),
            Transition(-498_128_400L, 30_600),
            Transition(-462_702_600L, 34_200),
            Transition(-451_733_400L, 30_600),
            Transition(-429_784_200L, 34_200),
            Transition(-418_296_600L, 30_600),
            Transition(-399_544_200L, 34_200),
            Transition(-387_451_800L, 30_600),
            Transition(-368_094_600L, 34_200),
            Transition(-356_002_200L, 30_600),
            Transition(-336_645_000L, 34_200),
            Transition(-324_552_600L, 30_600),
            Transition(-305_195_400L, 34_200),
            Transition(-293_103_000L, 30_600),
            Transition(-264_933_000L, 32_400),
            Transition(547_578_000L, 36_000),
            Transition(560_883_600L, 32_400),
            Transition(579_027_600L, 36_000),
            Transition(592_333_200L, 32_400),
        )
    private val daysBeforeMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

    fun dayAt(timestamp: Long): Long {
        val index = transitions.binarySearch { (it.epochSeconds * 1000).compareTo(timestamp) }
        val last = if (index >= 0) index else -index - 2
        val offsetMillis = (transitions.getOrNull(last)?.offsetSeconds ?: INITIAL_OFFSET_SECONDS) * 1000L
        val shiftedRemainder = timestamp % MILLIS_PER_DAY + offsetMillis
        val days = timestamp / MILLIS_PER_DAY + shiftedRemainder / MILLIS_PER_DAY
        return if (shiftedRemainder % MILLIS_PER_DAY < 0) days - 1 else days
    }

    fun parseDate(date: String): Long? {
        if (date.length != 10 || date[4] != '-' || date[7] != '-') return null
        val year = date.readDigits(0, 4) ?: return null
        val month = date.readDigits(5, 7) ?: return null
        val day = date.readDigits(8, 10) ?: return null
        if (year < 1 || month !in 1..12) return null
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val monthLength =
            when (month) {
                2 -> if (leap) 29 else 28
                4, 6, 9, 11 -> 30
                else -> 31
            }
        if (day !in 1..monthLength) return null

        val years = year - 1
        val leapDays = years / 4 - years / 100 + years / 400
        val elapsedDays = years * 365L + leapDays + daysBeforeMonth[month - 1] + day - 1
        return elapsedDays - 719_162 + if (leap && month > 2) 1 else 0
    }

    private fun String.readDigits(
        from: Int,
        until: Int,
    ): Int? {
        var value = 0
        for (index in from until until) {
            val digit = this[index]
            if (digit !in '0'..'9') return null
            value = value * 10 + (digit - '0')
        }
        return value
    }
}
