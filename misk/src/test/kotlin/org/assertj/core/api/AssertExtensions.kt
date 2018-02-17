package org.assertj.core.api

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.MapEntry

fun <KEY, VALUE> MapAssert<KEY, VALUE>.containsExactly(
        vararg p: Pair<KEY, VALUE>
): MapAssert<KEY, VALUE> {
    return containsExactly(*p.map { MapEntry.entry(it.first, it.second) }.toTypedArray())
}

fun <ACTUAL : CharSequence> AbstractCharSequenceAssert<*, ACTUAL>.isEqualToAsJson(
        expected: CharSequence
): AbstractCharSequenceAssert<*, ACTUAL> {
    // Normalize whitespace outside of field names and string values
    val regex = Regex("[^\\s\"']+|\"[^\"]*\"|'[^']*'")
    val normalizedActual = regex.findAll(actual).map { it.groupValues[0] }.joinToString(" ")
    val normalizedExpected = regex.findAll(expected).map { it.groupValues[0] }.joinToString(" ")
    objects.assertEqual(getWritableAssertionInfo(), normalizedActual, normalizedExpected)
    return this
}

class OrderingAssert<in A : Comparable<A>>(private val actual: A) {
    infix fun comesBefore(other: A) {
        assertThat(actual).isLessThan(other)
        assertThat(other).isGreaterThan(actual)
    }

    infix fun comesAfter(other: A) {
        assertThat(actual).isGreaterThan(other)
        assertThat(other).isLessThan(actual)
    }

    infix fun eq(other: A) {
        assertThat(actual).isEqualByComparingTo(other)
    }
}

fun <A : Comparable<A>> assertOrdering(vararg values: A) {
    values.forEachIndexed { index, value ->
        assertThat(value).isEqualByComparingTo(value)

        val before = if (index == 0) listOf() else values.take(index - 1)
        before.forEach {
            assertThat(value).isGreaterThan(it)
            assertThat(it).isLessThan(value)
        }

        val after = if (index == values.size - 1) listOf() else values.drop(index + 1)
        after.forEach {
            assertThat(value).isLessThan(it)
            assertThat(it).isGreaterThan(value)
        }
    }
}

fun <A : Comparable<A>> assertOrderOf(value: A) = OrderingAssert(value)