package org.assertj.core.api

import org.assertj.core.api.Assertions.assertThat

inline fun <reified KEY, VALUE> MapAssert<KEY, VALUE>.containsExactly(
    vararg p: Pair<KEY, VALUE>
): MapAssert<KEY, VALUE> {
    return isEqualTo(mapOf(*p))
}

fun <ACTUAL : CharSequence> AbstractCharSequenceAssert<*, ACTUAL>.isEqualToAsJson(
    expected: CharSequence
): AbstractCharSequenceAssert<*, ACTUAL> {
    // Normalize whitespace outside of field names and string values
    val regex = Regex("[^\\s\"']+|\"[^\"]*\"|'[^']*'")
    val normalizedActual = regex.findAll(actual)
        .map { it.groupValues[0] }
        .joinToString(" ")
    val normalizedExpected = regex.findAll(expected)
        .map { it.groupValues[0] }
        .joinToString(" ")
    objects.assertEqual(getWritableAssertionInfo(), normalizedActual, normalizedExpected)
    return this
}

// NB(mmihic): Explicitly tests ordering by comparing the ordering of element against
// each other. assertThat().isSorted() checks the entire ordering, but that can be difficult
// to debug vs comparing each element against each other
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
