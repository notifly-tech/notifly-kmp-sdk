package tech.notifly.kmp.segment

/** A device version with up to four numeric components and SemVer-style prerelease precedence. */
internal class DeviceVersion private constructor(
    private val segments: List<String>,
    private val prerelease: List<String>,
) : Comparable<DeviceVersion> {
    override fun compareTo(other: DeviceVersion): Int {
        for (index in 0 until 4) {
            val order = compareDigits(segments.getOrElse(index) { "0" }, other.segments.getOrElse(index) { "0" })
            if (order != 0) return order
        }
        if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
            return prerelease.isEmpty().compareTo(other.prerelease.isEmpty())
        }
        for (index in 0 until minOf(prerelease.size, other.prerelease.size)) {
            val left = prerelease[index]
            val right = other.prerelease[index]
            val leftNumeric = left.isAsciiDigits()
            val rightNumeric = right.isAsciiDigits()
            val order =
                when {
                    leftNumeric && rightNumeric -> compareDigits(left, right)
                    leftNumeric != rightNumeric -> if (leftNumeric) -1 else 1
                    else -> left.compareTo(right)
                }
            if (order != 0) return order
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        fun parse(value: String): DeviceVersion? {
            val buildSplit = value.split('+', limit = 2)
            val metadata = buildSplit.getOrNull(1)?.split('.')
            if (metadata?.any { !it.isVersionIdentifier() } == true) return null
            val versionSplit = buildSplit[0].removePrefix("v").split('-', limit = 2)
            val prerelease = versionSplit.getOrNull(1)?.split('.') ?: emptyList()
            if (prerelease.any { !it.isVersionIdentifier() || it.hasProhibitedLeadingZero() }) return null
            val core = versionSplit[0].split('.', limit = 5)
            if (core.size > 4 || core.any { !it.isAsciiDigits() }) return null
            val segments = core.map { it.trimStart('0').ifEmpty { "0" } }
            return DeviceVersion(segments, prerelease)
        }
    }
}

private fun String.isAsciiDigits(): Boolean = isNotEmpty() && all { it in '0'..'9' }

private fun String.isVersionIdentifier(): Boolean =
    isNotEmpty() && all { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' || it == '-' }

private fun String.hasProhibitedLeadingZero(): Boolean = length > 1 && first() == '0' && isAsciiDigits()

private fun compareDigits(
    left: String,
    right: String,
): Int {
    val order = left.length.compareTo(right.length)
    return if (order == 0) left.compareTo(right) else order
}
