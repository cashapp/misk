package org.assertj.core.api

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


