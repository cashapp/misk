package misk.concurrent

import java.time.Duration

/**
 * Abstraction for Thread.sleep() that allows for testing.
 */
interface Sleeper {
  fun sleep(duration: Duration)

  companion object {
    val DEFAULT: Sleeper = object : Sleeper {
      override fun sleep(duration: Duration) {
        Thread.sleep(duration.toMillis())
      }
    }
  }
}
