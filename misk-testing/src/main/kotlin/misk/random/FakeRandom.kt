package misk.random

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeRandom @Inject constructor() : Random() {
  var nextBoolean: Boolean? = null
  var nextInt: Int? = null
  var nextLong: Long? = null
  var nextFloat: Float? = null
  var nextDouble: Double? = null

  override fun nextBoolean(): Boolean = nextBoolean ?: super.nextBoolean()
  override fun nextInt(): Int = nextInt ?: super.nextInt()
  override fun nextInt(bound: Int): Int {
    if (nextInt == null) {
      return super.nextInt(bound)
    }

    require(bound > 0) {
      "bound must be positive, but was $bound"
    }

    require(nextInt!! >= 0) {
      "nextInt must be non-negative, but was $nextInt"
    }

    return nextInt!!.mod(bound)
  }
  override fun nextLong(): Long = nextLong ?: super.nextLong()
  override fun nextFloat(): Float = nextFloat ?: super.nextFloat()
  override fun nextDouble(): Double = nextDouble ?: super.nextDouble()
}

@Singleton
class FakeThreadLocalRandom @Inject constructor() : ThreadLocalRandom() {
  @Inject lateinit var fakeRandom: FakeRandom

  override fun current() = fakeRandom
}
