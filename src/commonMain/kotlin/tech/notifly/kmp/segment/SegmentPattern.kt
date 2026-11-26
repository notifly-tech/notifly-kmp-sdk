package tech.notifly.kmp.segment

private const val MAX_PATTERN_LENGTH = 2048
private const val MAX_GROUP_DEPTH = 64
private const val MAX_STATES = 4_096
private const val MAX_MATCH_STEPS = 1_500_000

/** A bounded, flag-free pattern that searches UTF-16 code units in one forward pass. */
internal class SegmentPattern private constructor(
    private val states: List<State>,
    private val start: Int,
    private val requiredLiteral: Char?,
) {
    /** Returns null when the match budget is exhausted before a result is known. */
    fun matches(text: String): Boolean? {
        var remainingSteps = MAX_MATCH_STEPS
        if (requiredLiteral != null && text.length <= remainingSteps) {
            val firstOccurrence = text.indexOf(requiredLiteral)
            if (firstOccurrence < 0) return false
            remainingSteps -= firstOccurrence + 1
        }

        val visited = IntArray(states.size) { -1 }
        val pending = IntArray(states.size)
        var active = mutableListOf<Int>()
        var next = mutableListOf<Int>()

        fun close(
            initial: Int,
            position: Int,
            result: MutableList<Int>,
        ): Boolean {
            var size = 0

            fun enqueue(index: Int) {
                if (visited[index] == position) return
                visited[index] = position
                pending[size++] = index
            }

            enqueue(initial)
            while (size > 0) {
                if (remainingSteps == 0) return false
                remainingSteps--
                val index = pending[--size]
                when (val state = states[index]) {
                    is State.Split -> {
                        enqueue(state.first)
                        enqueue(state.second)
                    }

                    is State.AtStart -> {
                        if (position == 0) enqueue(state.next)
                    }

                    is State.AtEnd -> {
                        if (position == text.length) enqueue(state.next)
                    }

                    is State.Consume -> {
                        result.add(index)
                    }

                    State.Accept -> {
                        Unit
                    }
                }
            }
            return true
        }

        for (position in 0..text.length) {
            if (!close(start, position, active)) return null
            if (visited[0] == position) return true
            if (position == text.length) break
            for (index in active) {
                if (remainingSteps == 0) return null
                remainingSteps--
                val state = states[index] as State.Consume
                if (state.test.matches(text[position]) && !close(state.next, position + 1, next)) return null
            }
            val previous = active
            active = next
            next = previous
            next.clear()
        }
        return false
    }

    companion object {
        fun compile(pattern: String): SegmentPattern? {
            if (pattern.length !in 1..MAX_PATTERN_LENGTH) return null
            return try {
                val expression = PatternParser(pattern).parse()
                val builder = StateBuilder()
                val start = builder.emit(expression, 0)
                SegmentPattern(builder.states.toList(), start, expression.requiredLiteral())
            } catch (_: InvalidPattern) {
                null
            }
        }
    }
}

private sealed interface State {
    object Accept : State

    class AtStart(
        val next: Int,
    ) : State

    class AtEnd(
        val next: Int,
    ) : State

    class Split(
        val first: Int,
        val second: Int,
    ) : State

    class Consume(
        val test: CharTest,
        val next: Int,
    ) : State
}

private sealed interface Expression {
    object Start : Expression

    object End : Expression

    class Take(
        val test: CharTest,
    ) : Expression

    class Sequence(
        val parts: List<Expression>,
    ) : Expression

    class Choice(
        val options: List<Expression>,
    ) : Expression

    class Repeat(
        val body: Expression,
        val minimum: Int,
        val maximum: Int?,
    ) : Expression
}

/** Finds a code unit that every match must contain, when the syntax proves one is required. */
private fun Expression.requiredLiteral(): Char? =
    when (this) {
        is Expression.Take -> (test as? CharTest.Literal)?.value
        is Expression.Sequence -> parts.asReversed().firstNotNullOfOrNull { it.requiredLiteral() }
        is Expression.Repeat -> if (minimum > 0) body.requiredLiteral() else null
        else -> null
    }

private sealed interface CharTest {
    val intervals: List<IntRange>

    fun matches(value: Char): Boolean {
        val code = value.code
        return intervals.binarySearch { range ->
            when {
                range.last < code -> -1
                range.first > code -> 1
                else -> 0
            }
        } >= 0
    }

    class Literal(
        val value: Char,
    ) : CharTest {
        override val intervals = listOf(value.code..value.code)

        override fun matches(value: Char): Boolean = value == this.value
    }

    class Range(
        private val first: Char,
        private val last: Char,
    ) : CharTest {
        override val intervals = listOf(first.code..last.code)

        override fun matches(value: Char): Boolean = value in first..last
    }

    class Set(
        members: List<CharTest>,
        negated: Boolean,
    ) : CharTest {
        override val intervals: List<IntRange> =
            mergeIntervals(members.flatMap { it.intervals }).let {
                if (negated) complementIntervals(it) else it
            }
    }

    enum class Named(
        override val intervals: List<IntRange>,
    ) : CharTest {
        DIGIT(listOf(0x30..0x39)),
        WORD(listOf(0x30..0x39, 0x41..0x5A, 0x5F..0x5F, 0x61..0x7A)),
        SPACE(
            listOf(
                0x09..0x0D,
                0x20..0x20,
                0xA0..0xA0,
                0x1680..0x1680,
                0x2000..0x200A,
                0x2028..0x2029,
                0x202F..0x202F,
                0x205F..0x205F,
                0x3000..0x3000,
                0xFEFF..0xFEFF,
            ),
        ),
        DOT(listOf(0..0x09, 0x0B..0x0C, 0x0E..0x2027, 0x202A..0xFFFF)),
    }
}

/** Keeps character-class membership independent of duplicate and overlapping members in the original pattern. */
private fun mergeIntervals(intervals: List<IntRange>): List<IntRange> {
    val merged = mutableListOf<IntRange>()
    for (interval in intervals.sortedBy { it.first }) {
        val previous = merged.lastOrNull()
        if (previous != null && interval.first <= previous.last + 1) {
            merged[merged.lastIndex] = previous.first..maxOf(previous.last, interval.last)
        } else {
            merged.add(interval)
        }
    }
    return merged
}

private fun complementIntervals(intervals: List<IntRange>): List<IntRange> =
    buildList {
        var start = 0
        for (interval in intervals) {
            if (start < interval.first) add(start until interval.first)
            start = interval.last + 1
        }
        if (start <= 0xFFFF) add(start..0xFFFF)
    }

private class InvalidPattern : IllegalArgumentException()

private class PatternParser(
    private val pattern: String,
) {
    private var offset = 0

    fun parse(): Expression {
        val expression = alternatives(0)
        if (peek() != null) throw InvalidPattern()
        return expression
    }

    private fun alternatives(depth: Int): Expression {
        val groups = mutableListOf(sequence(depth))
        while (accept('|')) groups.add(sequence(depth))
        return if (groups.size == 1) groups[0] else Expression.Choice(groups)
    }

    private fun sequence(depth: Int): Expression {
        val terms = mutableListOf<Expression>()
        while (peek() != null && peek() != ')' && peek() != '|') terms.add(quantified(atom(depth)))
        return if (terms.size == 1) terms[0] else Expression.Sequence(terms)
    }

    private fun atom(depth: Int): Expression =
        when (val char = next()) {
            '^' -> {
                Expression.Start
            }

            '$' -> {
                Expression.End
            }

            '.' -> {
                Expression.Take(CharTest.Named.DOT)
            }

            '\\' -> {
                Expression.Take(escape())
            }

            '[' -> {
                Expression.Take(characterClass())
            }

            '(' -> {
                if (depth >= MAX_GROUP_DEPTH || (accept('?') && !accept(':'))) throw InvalidPattern()
                val child = alternatives(depth + 1)
                expect(')')
                // Keep assertions inside a group distinct from ungrouped assertions when quantifying.
                Expression.Sequence(listOf(child))
            }

            '*', '+', '?', '{', '}', ']' -> {
                throw InvalidPattern()
            }

            else -> {
                Expression.Take(CharTest.Literal(char))
            }
        }

    private fun quantified(expression: Expression): Expression {
        val quantifier = peek()
        if (quantifier !in listOf('*', '+', '?', '{')) return expression
        if (expression == Expression.Start || expression == Expression.End) throw InvalidPattern()
        offset++
        return when (quantifier) {
            '*' -> {
                Expression.Repeat(expression, 0, null)
            }

            '+' -> {
                Expression.Repeat(expression, 1, null)
            }

            '?' -> {
                Expression.Repeat(expression, 0, 1)
            }

            else -> {
                val minimum = repeatCount()
                val maximum =
                    if (accept(',')) {
                        if (peek() == '}') null else repeatCount()
                    } else {
                        minimum
                    }
                expect('}')
                if (maximum != null && maximum < minimum) throw InvalidPattern()
                Expression.Repeat(expression, minimum, maximum)
            }
        }
    }

    private fun repeatCount(): Int {
        val start = offset
        while (peek()?.let { it in '0'..'9' } == true) offset++
        return pattern.substring(start, offset).toIntOrNull() ?: throw InvalidPattern()
    }

    private fun characterClass(): CharTest {
        val negated = accept('^')
        val members = mutableListOf<CharTest>()
        while (peek() != ']') {
            val first = classItem()
            if (peek() == '-' && pattern.getOrNull(offset + 1) != ']') {
                offset++
                val last = classItem()
                if (first !is CharTest.Literal || last !is CharTest.Literal || first.value > last.value) {
                    throw InvalidPattern()
                }
                members.add(CharTest.Range(first.value, last.value))
            } else {
                members.add(first)
            }
        }
        expect(']')
        return CharTest.Set(members, negated)
    }

    private fun classItem(): CharTest = if (accept('\\')) escape(inClass = true) else CharTest.Literal(next())

    private fun escape(inClass: Boolean = false): CharTest =
        when (val char = next()) {
            'd' -> CharTest.Named.DIGIT
            'w' -> CharTest.Named.WORD
            's' -> CharTest.Named.SPACE
            'D' -> CharTest.Set(listOf(CharTest.Named.DIGIT), negated = true)
            'W' -> CharTest.Set(listOf(CharTest.Named.WORD), negated = true)
            'S' -> CharTest.Set(listOf(CharTest.Named.SPACE), negated = true)
            'b' -> if (inClass) CharTest.Literal('\b') else throw InvalidPattern()
            'n' -> CharTest.Literal('\n')
            'r' -> CharTest.Literal('\r')
            't' -> CharTest.Literal('\t')
            'f' -> CharTest.Literal('\u000C')
            'v' -> CharTest.Literal('\u000B')
            'x' -> CharTest.Literal(hexEscape(2))
            'u' -> CharTest.Literal(hexEscape(4))
            in "\\.^$|?*+()[]{}-/" -> CharTest.Literal(char)
            else -> throw InvalidPattern()
        }

    private fun hexEscape(length: Int): Char {
        var code = 0
        repeat(length) {
            val digit =
                when (val char = next()) {
                    in '0'..'9' -> char - '0'
                    in 'A'..'F' -> char - 'A' + 10
                    in 'a'..'f' -> char - 'a' + 10
                    else -> throw InvalidPattern()
                }
            code = (code shl 4) or digit
        }
        return code.toChar()
    }

    private fun accept(char: Char): Boolean {
        if (peek() != char) return false
        offset++
        return true
    }

    private fun expect(char: Char) {
        if (!accept(char)) throw InvalidPattern()
    }

    private fun peek(): Char? = pattern.getOrNull(offset)

    private fun next(): Char = pattern.getOrNull(offset++) ?: throw InvalidPattern()
}

private class StateBuilder {
    val states = mutableListOf<State>(State.Accept)
    private var steps = 0

    fun emit(
        expression: Expression,
        next: Int,
    ): Int {
        if (++steps > MAX_STATES * 4) throw InvalidPattern()
        return when (expression) {
            Expression.Start -> {
                add(State.AtStart(next))
            }

            Expression.End -> {
                add(State.AtEnd(next))
            }

            is Expression.Take -> {
                add(State.Consume(expression.test, next))
            }

            is Expression.Sequence -> {
                expression.parts.foldRight(next, ::emit)
            }

            is Expression.Choice -> {
                val entries = expression.options.map { emit(it, next) }
                entries.reduce { left, right -> add(State.Split(left, right)) }
            }

            is Expression.Repeat -> {
                emitRepeat(expression, next)
            }
        }
    }

    private fun emitRepeat(
        expression: Expression.Repeat,
        next: Int,
    ): Int {
        val maximum = expression.maximum
        var continuation = next
        if (maximum == null) {
            val loop = add(State.Split(next, next))
            val body = emit(expression.body, loop)
            states[loop] = State.Split(body, next)
            continuation = loop
        } else {
            repeat(maximum - expression.minimum) {
                continuation = add(State.Split(emit(expression.body, continuation), next))
            }
        }
        repeat(expression.minimum) { continuation = emit(expression.body, continuation) }
        return continuation
    }

    private fun add(state: State): Int {
        if (states.size == MAX_STATES) throw InvalidPattern()
        states.add(state)
        return states.lastIndex
    }
}
