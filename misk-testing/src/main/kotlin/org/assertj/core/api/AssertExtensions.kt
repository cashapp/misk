package org.assertj.core.api

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.AssertionsForClassTypes.fail
import tools.jackson.core.JacksonException
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.json.JsonMapper

inline fun <reified KEY, VALUE> MapAssert<KEY, VALUE>.containsExactly(
  vararg p: Pair<KEY, VALUE>
): MapAssert<KEY, VALUE> {
  return isEqualTo(mapOf(*p))
}

private val objectMapper = JsonMapper.builder().enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build()

fun <ACTUAL : CharSequence> AbstractCharSequenceAssert<*, ACTUAL>.isEqualToAsJson(
  expected: CharSequence
): AbstractCharSequenceAssert<*, ACTUAL> {
  val parsedActual =
    try {
      objectMapper.readTree(actual.toString())
    } catch (e: JacksonException) {
      fail("Failed to parse actual value as JSON", e)
    }
  val parsedExpected =
    try {
      objectMapper.readTree(expected.toString())
    } catch (e: JacksonException) {
      fail("Failed to parse expected value as JSON", e)
    }
  objects.assertEqual(writableAssertionInfo, parsedActual, parsedExpected)
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
