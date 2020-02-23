package misk.concurrent

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.Service.State
import java.time.Clock
import java.time.Duration
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This is an implementation of ExecutorServiceFactory suitable for production use. It shuts down
 * all executors when the service shuts down.
 */
@Singleton
@Suppress("UnstableApiUsage") // Guava's Service is @Beta.
internal class RealExecutorServiceFactory @Inject constructor(
  private val clock: Clock
) : AbstractService(), ExecutorServiceFactory {
  private val executors = Collections.synchronizedMap(mutableMapOf<String, ExecutorService>())

  override fun doStart() {
    notifyStarted()
  }

  override fun doStop() {
    doStop(timeout = Duration.ofSeconds(30L))
  }

  internal fun doStop(timeout: Duration) {
    for ((_, executor) in executors) {
      executor.shutdown()
    }

    if (executors.all { it.value.isTerminated }) {
      notifyStopped()
      return
    }

    val deadlineMs = clock.millis() + timeout.toMillis()
    val awaitAllTerminated = object : Thread("RealExecutorServiceFactory") {
      override fun run() {
        try {
          for ((nameFormat, executor) in executors) {
            val timeoutLeftMs = deadlineMs - clock.millis()
            check(executor.awaitTermination(timeoutLeftMs, TimeUnit.MILLISECONDS)) {
              "$nameFormat took longer than $timeout to terminate"
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

  override fun single(nameFormat: String): ExecutorService {
    checkCreate()
    val threadFactory = threadFactory(nameFormat)
    return Executors.newSingleThreadExecutor(threadFactory)
        .also { executors[nameFormat] = it }
  }

  override fun fixed(nameFormat: String, threadCount: Int): ExecutorService {
    checkCreate()
    val threadFactory = threadFactory(nameFormat)
    return Executors.newFixedThreadPool(threadCount, threadFactory)
        .also { executors[nameFormat] = it }
  }

  override fun unbounded(nameFormat: String): ExecutorService {
    checkCreate()
    val threadFactory = threadFactory(nameFormat)
    return Executors.newCachedThreadPool(threadFactory)
        .also { executors[nameFormat] = it }
  }

  override fun scheduled(nameFormat: String, threadCount: Int): ScheduledExecutorService {
    checkCreate()
    val threadFactory = threadFactory(nameFormat)
    return Executors.newScheduledThreadPool(threadCount, threadFactory)
        .also { executors[nameFormat] = it }
  }

  private fun threadFactory(nameFormat: String): ThreadFactory {
    check(!executors.containsKey(nameFormat)) {
      "multiple executor services named $nameFormat - this could be a thread leak!"
    }

    return object : ThreadFactory {
      val nextId = AtomicLong(0)
      override fun newThread(runnable: Runnable): Thread {
        val name = String.format(nameFormat, nextId.getAndIncrement())
        return Thread(runnable, name)
      }
    }
  }

  private fun checkCreate() {
    val state = state()
    check(state == State.NEW || state == State.STARTING || state == State.RUNNING) {
      "cannot create an executor while service is $state"
    }
  }
}
