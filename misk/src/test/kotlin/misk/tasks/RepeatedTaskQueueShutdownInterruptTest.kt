package misk.tasks

import com.google.common.util.concurrent.Service
import jakarta.inject.Inject
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Verifies the [RepeatedTaskQueueConfig.interrupt_on_shutdown] behavior. These tests use real (not direct) executors
 * because the behavior under test only manifests when the task lambda runs on a separate worker thread that can be
 * blocked on I/O / sleep and then interrupted out of band.
 */
@MiskTest(startService = false)
internal class RepeatedTaskQueueShutdownInterruptTest {
  @MiskTestModule val module = TestModule()

  @Inject lateinit var repeatedTaskQueueFactory: RepeatedTaskQueueFactory

  @Test
  fun `awaitTerminated returns promptly when interrupt_on_shutdown is enabled`() {
    val queue =
      repeatedTaskQueueFactory.new(
        name = "interrupt-enabled-queue",
        config = RepeatedTaskQueueConfig(num_parallel_tasks = 1, interrupt_on_shutdown = true),
      )

    val taskStarted = CountDownLatch(1)
    val wasInterrupted = AtomicBoolean(false)

    queue.scheduleWithBackoff(timeBetweenRuns = Duration.ofMillis(0), initialDelay = Duration.ofMillis(0)) {
      taskStarted.countDown()
      try {
        // Simulate a long-blocking I/O call (e.g. SQS receiveMessage long-poll). Will return Status.NO_WORK if it ever
        // completes naturally — but we expect it to be interrupted long before then.
        Thread.sleep(60_000)
        Status.NO_WORK
      } catch (e: InterruptedException) {
        wasInterrupted.set(true)
        // Re-throw so RepeatedTaskQueue can observe the interrupt and short-circuit rescheduling.
        throw e
      }
    }

    queue.startAsync()
    queue.awaitRunning()
    assertThat(taskStarted.await(5, TimeUnit.SECONDS)).isTrue()

    val stopStartNanos = System.nanoTime()
    queue.stopAsync()
    queue.awaitTerminated(5, TimeUnit.SECONDS)
    val stopElapsedMs = (System.nanoTime() - stopStartNanos) / 1_000_000

    assertThat(wasInterrupted.get())
      .withFailMessage("expected the in-flight task to be interrupted, but it wasn't")
      .isTrue()
    assertThat(queue.state()).isEqualTo(Service.State.TERMINATED)
    // We don't assert a tight upper bound (CI is noisy) but the whole point is "much less than the 60s sleep".
    assertThat(stopElapsedMs)
      .withFailMessage("expected stop to complete in well under the 60s sleep, took ${stopElapsedMs}ms")
      .isLessThan(5_000)
  }

  @Test
  fun `interrupted task is not rescheduled`() {
    val queue =
      repeatedTaskQueueFactory.new(
        name = "no-reschedule-on-interrupt-queue",
        config = RepeatedTaskQueueConfig(num_parallel_tasks = 1, interrupt_on_shutdown = true),
      )

    val invocations = AtomicInteger(0)
    val taskStarted = CountDownLatch(1)

    queue.scheduleWithBackoff(timeBetweenRuns = Duration.ofMillis(0), initialDelay = Duration.ofMillis(0)) {
      invocations.incrementAndGet()
      taskStarted.countDown()
      Thread.sleep(60_000) // will throw InterruptedException
      Status.OK
    }

    queue.startAsync()
    queue.awaitRunning()
    assertThat(taskStarted.await(5, TimeUnit.SECONDS)).isTrue()

    queue.stopAsync()
    queue.awaitTerminated(5, TimeUnit.SECONDS)

    // Give a brief grace period to confirm the task does NOT get re-enqueued and re-run.
    Thread.sleep(200)
    assertThat(invocations.get())
      .withFailMessage("expected the interrupted task to not be rescheduled, but it ran ${invocations.get()} times")
      .isEqualTo(1)
  }

  @Test
  fun `awaitTerminated may not wait for in-flight task when interrupt_on_shutdown is disabled`() {
    // This test documents (rather than asserts strict timing on) the historical behavior: with the flag off,
    // awaitTerminated is allowed to return before the in-flight task lambda finishes. We assert the service reaches
    // TERMINATED quickly and that the in-flight task was NOT interrupted by us (proving the new code path is opt-in).
    val queue =
      repeatedTaskQueueFactory.new(
        name = "interrupt-disabled-queue",
        config = RepeatedTaskQueueConfig(num_parallel_tasks = 1, interrupt_on_shutdown = false),
      )

    val taskStarted = CountDownLatch(1)
    val taskFinished = CountDownLatch(1)
    val wasInterrupted = AtomicBoolean(false)

    queue.scheduleWithBackoff(timeBetweenRuns = Duration.ofMillis(0), initialDelay = Duration.ofMillis(0)) {
      taskStarted.countDown()
      try {
        Thread.sleep(500) // short enough to finish before the test ends
        Status.NO_WORK
      } catch (e: InterruptedException) {
        wasInterrupted.set(true)
        throw e
      } finally {
        taskFinished.countDown()
      }
    }

    queue.startAsync()
    queue.awaitRunning()
    assertThat(taskStarted.await(5, TimeUnit.SECONDS)).isTrue()

    queue.stopAsync()
    queue.awaitTerminated(5, TimeUnit.SECONDS)
    assertThat(queue.state()).isEqualTo(Service.State.TERMINATED)

    // The task should be allowed to drain naturally without being interrupted.
    assertThat(taskFinished.await(5, TimeUnit.SECONDS)).isTrue()
    assertThat(wasInterrupted.get())
      .withFailMessage("interrupt_on_shutdown=false should NOT interrupt the in-flight task lambda")
      .isFalse()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
    }
  }
}
