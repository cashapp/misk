package misk.concurrent

import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Abstraction for Thread.sleep() that allows for testing.
 */
interface Sleeper {
  fun sleep(duration: Duration)

  companion object {
    val DEFAULT: Sleeper = object : Sleeper {
      override fun sleep(duration: Duration) {
        val millis = duration.toMillis()
        val nanos = duration.minusMillis(millis).toNanos()
        Thread.sleep(millis, nanos.toInt())
      }
    }
  }
}
