package misk.time

import okio.Timeout
import java.io.InterruptedIOException
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Simulates a pool of a limited resource like database connections or disk bandwidth. Configure the
 * number of units that can be used concurrently. Use will wait for a maximum specified time to get
 * exclusive access to a resource, and then hold the resource for a specified time.
 */
class FakeResourcePool @Inject constructor() {
  /** Total number of resources available. */
  var total = 0
    @Synchronized set(value) {
      field = value
      notifyAll()
    }

  /** Total number of currently held. */
  private var busy = 0

  @Throws(InterruptedIOException::class)
  @Synchronized fun useResource(maxTimeToWait: Duration, timeToHold: Duration) {
    val timeout = Timeout()
    val maxNanosToWait = maxTimeToWait.toNanos()
    if (maxNanosToWait > 0) {
      timeout.deadline(maxNanosToWait, TimeUnit.NANOSECONDS)
    }

    while (busy >= total) {
      if (maxNanosToWait == 0L) throw InterruptedIOException("timeout")
      timeout.waitUntilNotified(this)
    }

    busy++
    waitNanosIgnoreNotifies(timeToHold.toNanos())
    busy--
    notifyAll()
  }
}
