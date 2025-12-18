package misk.concurrent

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Clock
import java.time.Duration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import misk.testing.FakeFixture

/**
 * [Sleeper] for testing that blocks threads calling [sleep], and checks whether the threads should wake using the
 * [tick()] method. An injected [Clock] is used to decide whether to wake a thread.
 */
@Singleton
class FakeSleeper @Inject constructor(private val clock: Clock) : FakeFixture(), Sleeper {
  private var count: Int by resettable { 0 }
  private var lastDuration: Duration? by resettable { null }
  private val lock by resettable { ReentrantLock() }
  private val wakeCondition by resettable { lock.newCondition() }
  private val waitForThreads by resettable { lock.newCondition() }
  private var numSleepingThreads by resettable { 0 }

  /** Check the current time and triggers any sleeping threads that are due to be awoken. */
  fun tick() {
    lock.withLock { wakeCondition.signalAll() }
  }

  /** Blocks until the given number of threads are asleep (as a result of calling [sleep] on this [FakeSleeper]). */
  fun waitForSleep(numThreads: Int) {
    lock.withLock {
      while (numSleepingThreads < numThreads) {
        waitForThreads.await()
      }
    }
  }

  override fun sleep(duration: Duration) {
    val sleepUntil = clock.millis() + duration.toMillis()
    lock.withLock {
      count++
      lastDuration = duration
      numSleepingThreads++
      while (clock.millis() < sleepUntil) {
        waitForThreads.signalAll()
        wakeCondition.await()
      }
      numSleepingThreads--
    }
  }

  /**
   * Returns the total number of times the [FakeSleeper] has been called. This is thread-safe, but the value may not be
   * meaningful if the sleeper is being used concurrently.
   */
  fun sleepCount(): Int {
    lock.withLock {
      return count
    }
  }

  /**
   * Returns the last duration [FakeSleeper] was called with. This is thread-safe, but the value may not be meaningful
   * if the sleeper is being used concurrently.
   */
  fun lastSleepDuration(): Duration? {
    lock.withLock {
      return lastDuration
    }
  }
}
