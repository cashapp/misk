package misk.tasks

import com.google.common.util.concurrent.Service
import com.google.inject.Provides
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.ServiceModule
import misk.backoff.ExponentialBackoff
import misk.backoff.FlatBackoff
import misk.backoff.retry
import misk.concurrent.ExplicitReleaseDelayQueue
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClockModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wisp.time.FakeClock
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.concurrent.withLock
import kotlin.test.assertEquals

@MiskTest(startService = true)
internal class RepeatedTaskQueueTest {
  @MiskTestModule val module = TestModule()

  @Inject lateinit var clock: FakeClock
  @Inject lateinit var taskQueue: RepeatedTaskQueue

  // Install another RepeatedTaskQueue to test guice is happy
  @Inject @field:Named("another") lateinit var anotherTaskQueue: RepeatedTaskQueue
  @Inject lateinit var pendingTasks: ExplicitReleaseDelayQueue<DelayedTask>
  @Inject lateinit var repeatedTaskQueueFactory: RepeatedTaskQueueFactory

  @BeforeEach fun initClock() {
    clock.add(Duration.ofDays(365 * 12))
  }

  @Test fun honorsInitialDelay() {
    taskQueue.schedule(Duration.ofSeconds(10)) {
      Result(Status.OK, Duration.ofSeconds(5))
    }

    val scheduled = waitForNextPendingTask()
    assertThat(scheduled.executionTime).isEqualTo(clock.instant().plus(Duration.ofSeconds(10)))
    assertThat(scheduled.getDelay(TimeUnit.SECONDS)).isEqualTo(10)

    clock.add(Duration.ofSeconds(7))
    assertThat(scheduled.getDelay(TimeUnit.SECONDS)).isEqualTo(3)
  }

  @Test fun scheduleMetrics() {
    var counter = 0
    taskQueue.schedule(Duration.ofSeconds(10)) {
      val status = when (counter) {
        0 -> Status.OK
        1 -> Status.FAILED
        2 -> Status.NO_RESCHEDULE
        else -> Status.NO_WORK
      }
      counter++
      Result(status, Duration.ofSeconds(5))
    }
    waitForNextPendingTask().task()
    assertThat(taskQueue.metrics.taskDuration.count("my-task-queue", "ok")).isEqualTo(1)
    waitForNextPendingTask().task()
    assertThat(taskQueue.metrics.taskDuration.count("my-task-queue", "failed")).isEqualTo(1)
    waitForNextPendingTask().task()
    assertThat(taskQueue.metrics.taskDuration.count("my-task-queue", "no_reschedule")).isEqualTo(1)
    waitForNextPendingTask().task()
    assertThat(taskQueue.metrics.taskDuration.count("my-task-queue", "no_work")).isEqualTo(1)
  }

  @Test fun scheduleBackoffMetrics() {
    var counter = 0
    taskQueue.scheduleWithBackoff(Duration.ofSeconds(10)) {
      val status = when (counter) {
        0 -> Status.OK
        1 -> Status.FAILED
        2 -> Status.NO_RESCHEDULE
        else -> Status.NO_WORK
      }
      counter++
      status
    }
    waitForNextPendingTask().task()
    assertThat(taskQueue.metrics.taskDuration.count("my-task-queue", "ok")).isEqualTo(1)
    waitForNextPendingTask().task()
    assertThat(taskQueue.metrics.taskDuration.count("my-task-queue", "failed")).isEqualTo(1)
    waitForNextPendingTask().task()
    assertThat(taskQueue.metrics.taskDuration.count("my-task-queue", "no_reschedule")).isEqualTo(1)
    waitForNextPendingTask().task()
    assertThat(taskQueue.metrics.taskDuration.count("my-task-queue", "no_work")).isEqualTo(1)
  }

  @Test fun ordersTasksByInitialDelay() {
    taskQueue.schedule(Duration.ofSeconds(10)) {
      Result(Status.OK, Duration.ofSeconds(5))
    }

    taskQueue.schedule(Duration.ofSeconds(9)) {
      Result(Status.OK, Duration.ofSeconds(5))
    }

    taskQueue.schedule(Duration.ofSeconds(13)) {
      Result(Status.OK, Duration.ofSeconds(5))
    }

    val firstScheduled = waitForNextPendingTask()
    assertThat(firstScheduled.getDelay(TimeUnit.SECONDS)).isEqualTo(9)
  }

  @Test fun honorsNextDelayReturnedByTask() {
    val latch = CountDownLatch(1)
    taskQueue.schedule(Duration.ofSeconds(3)) {
      // Re-run the task in 5 seconds
      if (latch.count > 0) latch.countDown()
      clock.add(Duration.ofSeconds(15))
      Result(Status.OK, Duration.ofSeconds(5))
    }

    // Allow the first task to be dispatched, then wait for it complete
    pendingTasks.release(1)
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()

    val nextScheduled = waitForNextPendingTask()
    assertThat(nextScheduled.getDelay(TimeUnit.SECONDS)).isEqualTo(5)
  }

  @Test fun honorsNoReschedule() {
    val latch = CountDownLatch(1)
    taskQueue.schedule(Duration.ofSeconds(3)) {
      if (latch.count > 0) latch.countDown()
      Result(Status.NO_RESCHEDULE, Duration.ofSeconds(0))
    }

    // Allow the first task to be dispatched, then wait for it complete
    pendingTasks.release(1)
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()

    // There should be no new task scheduled
    assertThat(pendingTasks.peekPending()).isNull()
  }

  @Test fun honorsBackoffTaskTimeBetweenRuns() {
    // Schedule a simple task with a fixed time between runs, confirm that after one execution
    // we re-schedule that task with the fixed backoff
    val latch = CountDownLatch(1)
    taskQueue.scheduleWithBackoff(Duration.ofSeconds(5)) {
      if (latch.count > 0) latch.countDown()
      clock.add(Duration.ofSeconds(12))
      Status.OK
    }

    // Release the task, then confirm it gets rescheduled with the backoff
    pendingTasks.release(1)
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()

    // There should be a new task scheduled at the time between runs
    val nextTask = waitForNextPendingTask()
    assertThat(nextTask.executionTime).isEqualTo(clock.instant().plusMillis(5000L))
    assertThat(nextTask.getDelay(TimeUnit.SECONDS)).isEqualTo(5)
  }

  @Test fun defaultsInitialDelayToTimeBetweenRuns() {
    taskQueue.scheduleWithBackoff(Duration.ofSeconds(5)) {
      Status.OK
    }

    val task = waitForNextPendingTask()
    assertThat(task.executionTime).isEqualTo(clock.instant().plusMillis(5000))
  }

  @Test fun honorsCustomInitialDelay() {
    taskQueue.scheduleWithBackoff(
      timeBetweenRuns = Duration.ofSeconds(5),
      initialDelay = Duration.ofSeconds(25)
    ) {
      Status.OK
    }

    val task = waitForNextPendingTask()
    assertThat(task.executionTime).isEqualTo(clock.instant().plusMillis(25000))
  }

  @Test fun honorsFailureBackoffOnException() {
    val lock = ReentrantLock()
    val taskCompleted = lock.newCondition()

    // Create a task that wakes up the main thread on execution, and then fails
    taskQueue.scheduleWithBackoff(
      timeBetweenRuns = Duration.ofSeconds(2),
      failureBackoff = ExponentialBackoff(Duration.ofSeconds(5), Duration.ofSeconds(60)),
      noWorkBackoff = ExponentialBackoff(Duration.ofSeconds(10), Duration.ofSeconds(100))
    ) {
      lock.withLock {
        clock.add(Duration.ofSeconds(35))
        taskCompleted.signalAll()
        throw IllegalArgumentException("this failed")
      }
    }

    // Repeat the task 5 times to keep expanding the backoff
    for (i in 0 until 5) {
      lock.withLock {
        pendingTasks.release(1)
        assertThat(taskCompleted.await(5, TimeUnit.SECONDS)).isTrue()
      }

      // Wait for the task to schedule its follow up
      waitForNextPendingTask()
    }

    // Next task should be scheduled at the failure backoff
    val nextTask = waitForNextPendingTask()
    assertThat(nextTask.getDelay(TimeUnit.SECONDS)).isEqualTo(60L)
  }

  @Test fun honorsFailureBackoffOnError() {
    val lock = ReentrantLock()
    val taskCompleted = lock.newCondition()

    // Create a task that wakes up the main thread on execution, and then fails
    taskQueue.scheduleWithBackoff(
      timeBetweenRuns = Duration.ofSeconds(2),
      failureBackoff = ExponentialBackoff(Duration.ofSeconds(5), Duration.ofSeconds(60)),
      noWorkBackoff = ExponentialBackoff(Duration.ofSeconds(10), Duration.ofSeconds(100))
    ) {
      lock.withLock {
        clock.add(Duration.ofSeconds(35))
        taskCompleted.signalAll()
        Status.FAILED
      }
    }

    // Repeat the task 5 times to keep expanding the backoff
    for (i in 0 until 5) {
      lock.withLock {
        pendingTasks.release(1)
        assertThat(taskCompleted.await(5, TimeUnit.SECONDS)).isTrue()
      }

      // Wait for the task to schedule its follow up
      waitForNextPendingTask()
    }

    // Next task should be scheduled at the failure backoff
    val nextTask = waitForNextPendingTask()
    assertThat(nextTask.getDelay(TimeUnit.SECONDS)).isEqualTo(60L)
  }

  @Test fun honorsNoWorkBackoff() {
    val lock = ReentrantLock()
    val taskCompleted = lock.newCondition()

    // Create a task that wakes up the main thread on execution, and then returns with no work
    taskQueue.scheduleWithBackoff(
      timeBetweenRuns = Duration.ofSeconds(2),
      failureBackoff = ExponentialBackoff(Duration.ofSeconds(5), Duration.ofSeconds(60)),
      noWorkBackoff = ExponentialBackoff(Duration.ofSeconds(10), Duration.ofSeconds(100))
    ) {
      lock.withLock {
        clock.add(Duration.ofSeconds(35))
        taskCompleted.signalAll()
        Status.NO_WORK
      }
    }

    // Repeat the task 5 times to keep expanding the backoff
    for (i in 0 until 5) {
      lock.withLock {
        pendingTasks.release(1)
        assertThat(taskCompleted.await(5, TimeUnit.SECONDS)).isTrue()
      }

      // Wait for the task to schedule its follow up
      waitForNextPendingTask()
    }

    // Next task should be scheduled at the no-work backoff
    val nextTask = waitForNextPendingTask()
    assertThat(nextTask.getDelay(TimeUnit.SECONDS)).isEqualTo(100L)
  }

  @Test fun resetFailureBackoffOnSuccess() {
    val lock = ReentrantLock()
    val taskCompleted = lock.newCondition()
    val returnStatus = AtomicReference(Status.FAILED)

    // Create a task that wakes up the main thread on execution, and then returns whatever
    // status the main thread has set
    taskQueue.scheduleWithBackoff(
      timeBetweenRuns = Duration.ofSeconds(2),
      failureBackoff = ExponentialBackoff(Duration.ofSeconds(5), Duration.ofSeconds(60)),
      noWorkBackoff = ExponentialBackoff(Duration.ofSeconds(10), Duration.ofSeconds(100))
    ) {
      lock.withLock {
        clock.add(Duration.ofSeconds(35))
        taskCompleted.signalAll()
        returnStatus.get()
      }
    }

    // Repeat the task 5 times to keep expanding the backoff
    for (i in 0 until 5) {
      lock.withLock {
        pendingTasks.release(1)
        assertThat(taskCompleted.await(5, TimeUnit.SECONDS)).isTrue()
      }

      // Wait for the task to schedule its follow up
      waitForNextPendingTask()
    }

    // Have the next iteration of the task succeed
    returnStatus.set(Status.OK)
    lock.withLock {
      pendingTasks.release(1)
      assertThat(taskCompleted.await(5, TimeUnit.SECONDS)).isTrue()
    }

    // And then fail again - should re-use the original failure backoff time
    waitForNextPendingTask()
    returnStatus.set(Status.FAILED)
    lock.withLock {
      pendingTasks.release(1)
      assertThat(taskCompleted.await(5, TimeUnit.SECONDS)).isTrue()
    }

    // Next task should be scheduled at the failure backoff initial time
    val nextTask = waitForNextPendingTask()
    assertThat(nextTask.getDelay(TimeUnit.SECONDS)).isEqualTo(5L)
  }

  @Test fun resetFailureBackoffWhenNoWork() {
    val lock = ReentrantLock()
    val taskCompleted = lock.newCondition()
    val returnStatus = AtomicReference(Status.FAILED)

    // Create a task that wakes up the main thread on execution, and then returns whatever
    // status the main thread has set
    taskQueue.scheduleWithBackoff(
      timeBetweenRuns = Duration.ofSeconds(2),
      failureBackoff = ExponentialBackoff(Duration.ofSeconds(5), Duration.ofSeconds(60)),
      noWorkBackoff = ExponentialBackoff(Duration.ofSeconds(10), Duration.ofSeconds(100))
    ) {
      lock.withLock {
        clock.add(Duration.ofSeconds(35))
        taskCompleted.signalAll()
        returnStatus.get()
      }
    }

    // Repeat the task 5 times to keep expanding the backoff
    for (i in 0 until 5) {
      lock.withLock {
        pendingTasks.release(1)
        assertThat(taskCompleted.await(5, TimeUnit.SECONDS)).isTrue()
      }

      // Wait for the task to schedule its follow up
      waitForNextPendingTask()
    }

    // Have the next iteration of the task return with NO_WORK
    returnStatus.set(Status.NO_WORK)
    lock.withLock {
      pendingTasks.release(1)
      assertThat(taskCompleted.await(5, TimeUnit.SECONDS)).isTrue()
    }

    // And then fail again - should re-use the original failure backoff time
    waitForNextPendingTask()
    returnStatus.set(Status.FAILED)
    lock.withLock {
      pendingTasks.release(1)
      assertThat(taskCompleted.await(5, TimeUnit.SECONDS)).isTrue()
    }

    // Next task should be scheduled at the failure backoff initial time
    val nextTask = waitForNextPendingTask()
    assertThat(nextTask.getDelay(TimeUnit.SECONDS)).isEqualTo(5L)
  }

  @Test fun resetNoWorkBackoffOnSuccess() {
    val lock = ReentrantLock()
    val taskCompleted = lock.newCondition()
    val returnStatus = AtomicReference(Status.NO_WORK)

    // Create a task that wakes up the main thread on execution, and then returns whatever
    // status the main thread has set
    taskQueue.scheduleWithBackoff(
      timeBetweenRuns = Duration.ofSeconds(2),
      failureBackoff = ExponentialBackoff(Duration.ofSeconds(5), Duration.ofSeconds(60)),
      noWorkBackoff = ExponentialBackoff(Duration.ofSeconds(10), Duration.ofSeconds(100))
    ) {
      lock.withLock {
        clock.add(Duration.ofSeconds(35))
        taskCompleted.signalAll()
        returnStatus.get()
      }
    }

    // Repeat the task 5 times to keep expanding the backoff
    for (i in 0 until 5) {
      lock.withLock {
        pendingTasks.release(1)
        assertThat(taskCompleted.await(5, TimeUnit.SECONDS)).isTrue()
      }

      // Wait for the task to schedule its follow up
      waitForNextPendingTask()
    }

    // Have the next iteration of the task succeed
    returnStatus.set(Status.OK)
    lock.withLock {
      pendingTasks.release(1)
      assertThat(taskCompleted.await(5, TimeUnit.SECONDS)).isTrue()
    }

    // And then specify no work again - should re-use the original failure backoff time
    waitForNextPendingTask()
    returnStatus.set(Status.NO_WORK)
    lock.withLock {
      pendingTasks.release(1)
      assertThat(taskCompleted.await(5, TimeUnit.SECONDS)).isTrue()
    }

    // Next task should be scheduled at the no-work backoff initial time
    val nextTask = waitForNextPendingTask()
    assertThat(nextTask.getDelay(TimeUnit.SECONDS)).isEqualTo(10L)
  }

  @Test fun resetNoWorkBackoffOnFailure() {
    val lock = ReentrantLock()
    val taskCompleted = lock.newCondition()
    val returnStatus = AtomicReference(Status.NO_WORK)

    // Create a task that wakes up the main thread on execution, and then returns whatever
    // status the main thread has set
    taskQueue.scheduleWithBackoff(
      timeBetweenRuns = Duration.ofSeconds(2),
      failureBackoff = ExponentialBackoff(Duration.ofSeconds(5), Duration.ofSeconds(60)),
      noWorkBackoff = ExponentialBackoff(Duration.ofSeconds(10), Duration.ofSeconds(100))
    ) {
      lock.withLock {
        clock.add(Duration.ofSeconds(35))
        taskCompleted.signalAll()
        returnStatus.get()
      }
    }

    // Repeat the task 5 times to keep expanding the backoff
    for (i in 0 until 5) {
      lock.withLock {
        pendingTasks.release(1)
        assertThat(taskCompleted.await(5, TimeUnit.SECONDS)).isTrue()
      }

      // Wait for the task to schedule its follow up
      waitForNextPendingTask()
    }

    // Have the next iteration of the task fail
    returnStatus.set(Status.FAILED)
    lock.withLock {
      pendingTasks.release(1)
      assertThat(taskCompleted.await(5, TimeUnit.SECONDS)).isTrue()
    }

    // And then specify no work again - should re-use the original failure backoff time
    waitForNextPendingTask()
    returnStatus.set(Status.NO_WORK)
    lock.withLock {
      pendingTasks.release(1)
      assertThat(taskCompleted.await(5, TimeUnit.SECONDS)).isTrue()
    }

    // Next task should be scheduled at the no-work backoff initial time
    val nextTask = waitForNextPendingTask()
    assertThat(nextTask.getDelay(TimeUnit.SECONDS)).isEqualTo(10L)
  }

  @Test fun sortTaskByDelay() {
    // Schedule three tasks, each returning different status (so we can differentiate them)
    val queue = PriorityBlockingQueue<DelayedTask>()
    queue.add(
      DelayedTask(clock, clock.instant().plusMillis(100)) {
        Result(Status.OK, Duration.ofMillis(100))
      }
    )
    queue.add(
      DelayedTask(clock, clock.instant().plusMillis(75)) {
        Result(Status.FAILED, Duration.ofMillis(100))
      }
    )
    queue.add(
      DelayedTask(clock, clock.instant().plusMillis(125)) {
        Result(Status.NO_WORK, Duration.ofMillis(100))
      }
    )

    // Drain the tasks and make sure they were queued in the proper order
    assertThat(queue.poll().task().status).isEqualTo(Status.FAILED)
    assertThat(queue.poll().task().status).isEqualTo(Status.OK)
    assertThat(queue.poll().task().status).isEqualTo(Status.NO_WORK)
  }

  @Test fun handlesUncaughtThrowableFromTask() {
    val latch = CountDownLatch(1)
    taskQueue.schedule(Duration.ofSeconds(3)) {
      if (latch.count > 0) latch.countDown()
      throw Throwable("a throwable!")
    }

    // Allow the first task to be dispatched, then wait for it complete
    pendingTasks.release(1)
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()

    val nextScheduled = waitForNextPendingTask()
    assertThat(nextScheduled.getDelay(TimeUnit.SECONDS)).isEqualTo(3)
  }

  @Test fun handlesUncaughtThrowableFromTaskWithRetryDelay() {
    val latch = CountDownLatch(1)
    taskQueue.schedule(Duration.ofSeconds(3), Duration.ofSeconds(5)) {
      if (latch.count > 0) latch.countDown()
      throw Throwable("a throwable!")
    }

    // Allow the first task to be dispatched, then wait for it complete
    pendingTasks.release(1)
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()

    val nextScheduled = waitForNextPendingTask()
    assertThat(nextScheduled.getDelay(TimeUnit.SECONDS)).isEqualTo(5)
  }

  @Test fun `terminates when it is shut down`() {
    val queues = mutableListOf(taskQueue)
    // Each queue should have its own backingStorage, but it can't be guaranteed, so test with worst case
    // val queuesPendingTasks = mutableListOf(pendingTasks)
    val numberOfNewQueues = 15
    for (i in 0..numberOfNewQueues) {
      // queuesPendingTasks.add(ExplicitReleaseDelayQueue<DelayedTask>())
      queues.add(
        i,
        repeatedTaskQueueFactory.forTesting(
          name = "queue-$i",
          backingStorage = pendingTasks // queuesPendingTasks[i]
        )
      )
    }

    for (i in 0..queues.lastIndex) {
      if (Service.State.RUNNING != queues[i].state()) queues[i].startAsync()
    }

    for (i in 0..queues.lastIndex) {
      queues[i].schedule(Duration.ZERO, Duration.ofMillis(100L)) {
        Result(Status.OK, Duration.ofMillis(100))
      }
    }

    for (i in 0..queues.lastIndex) {
      queues[i].stopAsync()
    }

    var waitToTerminate = true
    var attempts = 0
    while (waitToTerminate && attempts < 3) {
      Thread.sleep(2500)
      waitToTerminate = false
      for (i in 0..queues.lastIndex) {
        if (Service.State.TERMINATED != queues[i].state()) {
          waitToTerminate = true
        }
      }
      attempts++
    }

    for (i in 0..queues.lastIndex) {
      assertEquals(
        Service.State.TERMINATED, queues[i].state(),
        "Failed to TERMINATE for queue $i: ${queues[i].name}"
      )
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(Modules.override(MiskTestingServiceModule()).with(FakeClockModule()))
      install(ServiceModule<RepeatedTaskQueue>())
    }

    @Provides @Singleton
    fun repeatedTaskQueueBackingStorage(): ExplicitReleaseDelayQueue<DelayedTask> {
      return ExplicitReleaseDelayQueue()
    }

    @Provides @Singleton
    fun repeatedTaskQueue(
      queueFactory: RepeatedTaskQueueFactory,
      backingStorage: ExplicitReleaseDelayQueue<DelayedTask>
    ): RepeatedTaskQueue {
      return queueFactory.forTesting("my-task-queue", backingStorage)
    }

    @Provides @Singleton @Named("another")
    fun anotherRepeatedTaskQueue(
      queueFactory: RepeatedTaskQueueFactory,
      backingStorage: ExplicitReleaseDelayQueue<DelayedTask>
    ): RepeatedTaskQueue {
      return queueFactory.forTesting("another-task-queue", backingStorage)
    }
  }

  private fun waitForNextPendingTask(): DelayedTask =
    retry(5, FlatBackoff(Duration.ofMillis(200))) {
      pendingTasks.peekPending()!!
    }
}
