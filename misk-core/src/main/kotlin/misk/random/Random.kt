package misk.random

import com.google.inject.Inject
import com.google.inject.Singleton

/**
 * Abstraction for Java's Random that allows for testing.
 */
@Singleton
open class Random @Inject constructor() : java.util.Random()

/**
 * Abstraction for Java's ThreadLocalRandom that allows for testing.
 */
@Singleton
open class ThreadLocalRandom @Inject constructor() {
  open fun current(): java.util.Random = java.util.concurrent.ThreadLocalRandom.current()
}
