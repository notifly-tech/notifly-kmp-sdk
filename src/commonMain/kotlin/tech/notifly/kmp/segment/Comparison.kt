package tech.notifly.kmp.segment

private fun String.isDecimalLiteral(): Boolean {
    val firstDigit = if (startsWith('-')) 1 else 0
    var cursor = firstDigit
    while (cursor < length && this[cursor] in '0'..'9') cursor++
    if (cursor == firstDigit) return false
    if (cursor == length) return true
    if (this[cursor] != '.') return false

    val fractionStart = ++cursor
    while (cursor < length && this[cursor] in '0'..'9') cursor++
    return cursor == length && cursor > fractionStart
}

private enum class ValueKind {
    TEXT,
    INT,
    BOOL,
    ;

    fun convert(value: Any?): Any? =
        when (this) {
            TEXT -> {
                when (value) {
                    is String -> value
                    is Boolean -> value.toString()
                    is Number -> value.toDouble().takeIf { it.isFinite() }?.let(::ecmaNumberText)
                    else -> null
                }
            }

            INT -> {
                when (value) {
                    is Number -> value.toDouble()
                    is String -> value.takeIf { it.isDecimalLiteral() }?.toDoubleOrNull()
                    else -> null
                }?.takeIf { it.isFinite() }?.let { if (it == 0.0) 0.0 else it }
            }

            BOOL -> {
                when (value) {
                    is Boolean -> {
                        value
                    }

                    is Number -> {
                        when (value.toDouble()) {
                            1.0 -> true
                            0.0 -> false
                            else -> null
                        }
                    }

                    is String -> {
                        when (value.lowercase()) {
                            "true", "1" -> true
                            "false", "0" -> false
                            else -> null
                        }
                    }

                    else -> {
                        null
                    }
                }
            }
        }
}

private enum class Operation {
    PRESENT,
    EQUAL,
    GREATER,
    GREATER_OR_EQUAL,
    LESS,
    LESS_OR_EQUAL,
    INCLUDES,
    SUBSTRING_IN_ARRAY,
    PREFIX,
    SUFFIX,
    SUBSTRING,
    REGEX,
    ;

    fun accepts(kind: ValueKind): Boolean =
        when (this) {
            GREATER, GREATER_OR_EQUAL, LESS, LESS_OR_EQUAL -> kind != ValueKind.BOOL
            PREFIX, SUFFIX, SUBSTRING, REGEX, SUBSTRING_IN_ARRAY -> kind == ValueKind.TEXT
            else -> true
        }

    val textMustBeNonempty: Boolean
        get() = this in setOf(PREFIX, SUFFIX, SUBSTRING, REGEX, SUBSTRING_IN_ARRAY)

    val ordered: Boolean
        get() = this == GREATER || this == GREATER_OR_EQUAL || this == LESS || this == LESS_OR_EQUAL

    fun matchesOrder(order: Int): Boolean =
        when (this) {
            GREATER -> order > 0
            GREATER_OR_EQUAL -> order >= 0
            LESS -> order < 0
            LESS_OR_EQUAL -> order <= 0
            else -> order == 0
        }
}

private data class Operator(
    val operation: Operation,
    val negated: Boolean = false,
)

private val operators =
    mapOf(
        "IS_NULL" to Operator(Operation.PRESENT, negated = true),
        "IS_NOT_NULL" to Operator(Operation.PRESENT),
        "=" to Operator(Operation.EQUAL),
        "<>" to Operator(Operation.EQUAL, negated = true),
        ">" to Operator(Operation.GREATER),
        ">=" to Operator(Operation.GREATER_OR_EQUAL),
        "<" to Operator(Operation.LESS),
        "<=" to Operator(Operation.LESS_OR_EQUAL),
        "@>" to Operator(Operation.INCLUDES),
        "NOT_INCLUDE" to Operator(Operation.INCLUDES, negated = true),
        "CONTAINS_ANY_OF" to Operator(Operation.SUBSTRING_IN_ARRAY),
        "starts_with" to Operator(Operation.PREFIX),
        "does_not_start_with" to Operator(Operation.PREFIX, negated = true),
        "ends_with" to Operator(Operation.SUFFIX),
        "does_not_end_with" to Operator(Operation.SUFFIX, negated = true),
        "contains" to Operator(Operation.SUBSTRING),
        "does_not_contain" to Operator(Operation.SUBSTRING, negated = true),
        "matches_regex" to Operator(Operation.REGEX),
        "does_not_match_regex" to Operator(Operation.REGEX, negated = true),
    )

private data class Target(
    val value: Any,
    val pattern: SegmentPattern?,
    val version: DeviceVersion?,
)

internal class Comparison private constructor(
    private val operator: Operator,
    private val kind: ValueKind?,
    private val staticTarget: Target?,
    private val comparesVersions: Boolean,
) {
    fun matches(
        source: Any?,
        dynamicValue: Any? = null,
    ): Boolean {
        val operation = operator.operation
        if (operation == Operation.PRESENT) return (source != null) != operator.negated
        val kind = kind ?: return false
        val target = staticTarget ?: prepareTarget(dynamicValue, kind, operation, comparesVersions) ?: return false
        val positive = positiveMatch(operation, kind, source, target) ?: return false
        return positive != operator.negated
    }

    private fun positiveMatch(
        operation: Operation,
        kind: ValueKind,
        source: Any?,
        target: Target,
    ): Boolean? {
        if (operation == Operation.INCLUDES) {
            return (source as? List<*>)?.any { kind.convert(it) == target.value } == true
        }
        if (operation == Operation.SUBSTRING_IN_ARRAY) {
            return (source as? List<*>)?.any {
                (kind.convert(it) as? String)?.contains(target.value as String) == true
            } == true
        }

        val left = kind.convert(source) ?: return false
        if (comparesVersions) {
            val leftVersion = DeviceVersion.parse(left as String)
            if (leftVersion != null && target.version != null) {
                return operation.matchesOrder(leftVersion.compareTo(target.version))
            }
            if (operation.ordered) return false
        }
        return when (operation) {
            Operation.EQUAL -> {
                left == target.value
            }

            Operation.PREFIX -> {
                (left as String).startsWith(target.value as String)
            }

            Operation.SUFFIX -> {
                (left as String).endsWith(target.value as String)
            }

            Operation.SUBSTRING -> {
                (left as String).contains(target.value as String)
            }

            Operation.REGEX -> {
                target.pattern?.matches(left as String)
            }

            else -> {
                val order =
                    when (kind) {
                        ValueKind.INT -> (left as Double).compareTo(target.value as Double)
                        ValueKind.TEXT -> (left as String).compareTo(target.value as String)
                        ValueKind.BOOL -> return false
                    }
                operation.matchesOrder(order)
            }
        }
    }

    companion object {
        fun compile(
            wireOperator: String?,
            type: String?,
            value: Any?,
            dynamicTarget: Boolean,
            version: Boolean,
        ): Comparison? {
            val operator = operators[wireOperator] ?: return null
            val operation = operator.operation
            if (operation == Operation.PRESENT) return Comparison(operator, null, null, comparesVersions = false)
            val kind = ValueKind.values().firstOrNull { it.name == type } ?: return null
            if (!operation.accepts(kind)) return null

            val comparesVersions =
                version && kind == ValueKind.TEXT && (operation == Operation.EQUAL || operation.ordered)
            if (dynamicTarget) return Comparison(operator, kind, null, comparesVersions)
            if (kind == ValueKind.TEXT && value !is String) return null
            if (kind == ValueKind.BOOL && value !is Boolean) return null
            val target = prepareTarget(value, kind, operation, comparesVersions) ?: return null
            return Comparison(operator, kind, target, comparesVersions)
        }
    }
}

internal fun compileComparison(
    operator: String?,
    type: String?,
    value: Any?,
    dynamicTarget: Boolean = false,
    version: Boolean = false,
): Comparison? = Comparison.compile(operator, type, value, dynamicTarget, version)

private fun prepareTarget(
    value: Any?,
    kind: ValueKind,
    operation: Operation,
    comparesVersions: Boolean,
): Target? {
    val converted = kind.convert(value) ?: return null
    if (operation.textMustBeNonempty && (converted as String).isEmpty()) return null
    val pattern = if (operation == Operation.REGEX) SegmentPattern.compile(converted as String) ?: return null else null
    val version = if (comparesVersions) DeviceVersion.parse(converted as String) else null
    return Target(converted, pattern, version)
}
