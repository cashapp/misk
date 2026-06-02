package misk.jooq.testutils

fun <T, U> Collection<T>.cartesianProduct(other: Collection<U>): List<Pair<T, U>> =
  this.flatMap { lhsElem -> other.map { rhsElem -> lhsElem to rhsElem } }
