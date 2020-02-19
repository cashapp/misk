package misk.concurrent

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.Service.State
import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This is an implementation of ExecutorServiceFactory suitable for production use. It shuts down
 * all executors when the service shuts down.
 */
@Singleton
@Suppress("UnstableApiUsage") // Guava's Service is @Beta.
internal class RealExecutorServiceFactory private constructor(
  private val clock: Clock,
  private val executors: CopyOnWriteArrayList<ExecutorService>,
  private val threadFactory: ThreadFactory
) : AbstractService(), ExecutorServiceFactory {

  @Inject constructor(clock: Clock)
      : this(clock, CopyOnWriteArrayList(), Executors.defaultThreadFactory())

  override fun doStart() {
    notifyStarted()
  }

  override fun doStop() {
    doStop(timeout = Duration.ofSeconds(30L))
  }

  internal fun doStop(timeout: Duration) {
    for (executor in executors) {
      executor.shutdown()
    }

    if (executors.all { it.isTerminated }) {
      notifyStopped()
      return
    }

    val deadlineMs = clock.millis() + timeout.toMillis()
    val awaitAllTerminated = object : Thread("RealExecutorServiceFactory") {
      override fun run() {
        try {
          for (executorService in executors) {
            val timeoutLeftMs = deadlineMs - clock.millis()
            check(executorService.awaitTermination(timeoutLeftMs, TimeUnit.MILLISECONDS)) {
              "$executorService took longer than $timeout to terminate"
            }
          }
          notifyStopped()
        } catch (t: Throwable) {
          notifyFailed(t)
        }
      }
    }
    awaitAllTerminated.start()
  }

  override fun withThreadFactory(threadFactory: ThreadFactory) =
      RealExecutorServiceFactory(clock, executors, threadFactory)

  override fun named(nameFormat: String) =
      withThreadFactory(ThreadFactoryBuilder().setNameFormat(nameFormat).build())

  override fun single(): ExecutorService {
    checkCreate()
    return Executors.newSingleThreadExecutor(threadFactory)
        .also { executors += it }
  }

  override fun fixed(threadCount: Int): ExecutorService {
    checkCreate()
    return Executors.newFixedThreadPool(threadCount, threadFactory)
        .also { executors += it }
  }

  override fun unbounded(): ExecutorService {
    checkCreate()
    return Executors.newCachedThreadPool(threadFactory)
        .also { executors += it }
  }

  override fun scheduled(threadCount: Int): ScheduledExecutorService {
    checkCreate()
    return Executors.newScheduledThreadPool(threadCount, threadFactory)
        .also { executors += it }
  }

  private fun checkCreate() {
    val state = state()
    check(state == State.NEW || state == State.STARTING || state == State.RUNNING) {
      "cannot create an executor while service is $state"
    }
  }
}
