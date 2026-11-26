package tech.notifly.kmp.segment

import kotlin.math.abs

private const val DECIMAL_BASE = 1_000_000_000L
private const val SIGNIFICAND_MASK = (1L shl 52) - 1
private const val MAX_EXACT_INTEGER = 9_007_199_254_740_992.0

/** Formats a finite binary64 value using the decimal selection and notation rules of ECMAScript. */
internal fun ecmaNumberText(value: Double): String {
    val magnitude = abs(value)
    if (magnitude <= MAX_EXACT_INTEGER && magnitude == magnitude.toLong().toDouble()) {
        return value.toLong().toString()
    }

    val bits = magnitude.toBits()
    val encodedExponent = (bits ushr 52).toInt()
    val fraction = bits and SIGNIFICAND_MASK
    val significand = if (encodedExponent == 0) fraction else fraction + (1L shl 52)
    val binaryExponent = if (encodedExponent == 0) -1074 else encodedExponent - 1075
    val scale = minOf(binaryExponent, 0)
    val exactDigits = exactDecimalDigits(significand, binaryExponent)
    val (digits, exponent) = shortestDecimal(magnitude, exactDigits, scale)
    val text = formatDecimal(digits, exponent + digits.length)
    return if (value < 0.0) "-$text" else text
}

/** Computes the exact digits in base 10, using chunks to keep integer arithmetic within Long. */
private fun exactDecimalDigits(
    significand: Long,
    binaryExponent: Int,
): String {
    val chunks = mutableListOf(significand % DECIMAL_BASE, significand / DECIMAL_BASE)
    val factor = if (binaryExponent < 0) 5L else 2L
    val block = if (binaryExponent < 0) 12 else 29
    var remaining = abs(binaryExponent)
    while (remaining > 0) {
        val power = minOf(remaining, block)
        var multiplier = 1L
        repeat(power) { multiplier *= factor }
        var carry = 0L
        for (index in chunks.indices) {
            val product = chunks[index] * multiplier + carry
            chunks[index] = product % DECIMAL_BASE
            carry = product / DECIMAL_BASE
        }
        if (carry != 0L) chunks.add(carry)
        remaining -= power
    }
    while (chunks.size > 1 && chunks.last() == 0L) chunks.removeAt(chunks.lastIndex)
    return buildString {
        append(chunks.last())
        for (index in chunks.lastIndex - 1 downTo 0) append(chunks[index].toString().padStart(9, '0'))
    }
}

private fun shortestDecimal(
    value: Double,
    exact: String,
    scale: Int,
): Pair<String, Int> {
    for (length in 1..minOf(exact.length, 17)) {
        val lower = exact.substring(0, length).toLong()
        val exponent = scale + exact.length - length
        val firstDiscarded = exact.getOrNull(length) ?: '0'
        val roundUp =
            firstDiscarded > '5' ||
                (firstDiscarded == '5' && ((lower % 2) != 0L || exact.indexOfNonZeroAfter(length)))
        val nearest = if (roundUp) lower + 1 else lower
        val other = if (roundUp) lower else lower + 1
        val candidate =
            when {
                "${nearest}e$exponent".toDouble() == value -> nearest
                "${other}e$exponent".toDouble() == value -> other
                else -> continue
            }
        val candidateText = candidate.toString()
        val digits = candidateText.trimEnd('0')
        return digits to (exponent + candidateText.length - digits.length)
    }
    val digits = exact.trimEnd('0')
    return digits to (scale + exact.length - digits.length)
}

private fun String.indexOfNonZeroAfter(index: Int): Boolean {
    for (offset in index + 1 until length) {
        if (this[offset] != '0') return true
    }
    return false
}

private fun formatDecimal(
    digits: String,
    point: Int,
): String =
    when {
        point in digits.length..21 -> {
            digits + "0".repeat(point - digits.length)
        }

        point in 1..21 -> {
            digits.substring(0, point) + "." + digits.substring(point)
        }

        point in -5..0 -> {
            "0." + "0".repeat(-point) + digits
        }

        else -> {
            val coefficient = if (digits.length == 1) digits else digits[0] + "." + digits.substring(1)
            val exponent = point - 1
            val sign = if (exponent >= 0) "+" else ""
            "${coefficient}e$sign$exponent"
        }
    }
