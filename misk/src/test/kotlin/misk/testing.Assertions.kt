package misk

import org.assertj.core.api.MapAssert
import org.assertj.core.data.MapEntry

fun <KEY, VALUE> MapAssert<KEY, VALUE>.containsExactly(
    vararg p: Pair<KEY, VALUE>
): MapAssert<KEY, VALUE> {
  return containsExactly(*p.map { MapEntry.entry(it.first, it.second) }.toTypedArray())
}
