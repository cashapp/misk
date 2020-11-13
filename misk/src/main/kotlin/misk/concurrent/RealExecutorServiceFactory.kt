package misk.concurrent

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.Service.State
import com.google.inject.Inject
import io.opentracing.Tracer
import io.opentracing.contrib.concurrent.TracedExecutorService
import io.opentracing.contrib.concurrent.TracedScheduledExecutorService
import java.time.Clock
import java.time.Duration
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
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
  @Inject(optional = true) var tracer: Tracer? = null

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

  // The traceWithActiveSpanOnly=false flag below means we will create a new root span if there is
  // not an ongoing active span at the point of handing over a task to the ExecutorService. This
  // means we run the risk of having too many root spans but I prefer to err on the side of too much
  // visibility. This may cause unnecessary noise though so we may need to change this as we try it
  // out in production.

  internal fun maybeTrace(executorService: ExecutorService): ExecutorService =
      if (tracer != null) {
        TracedExecutorService(executorService, tracer, /*traceWithActiveSpanOnly=*/false)
      } else {
        executorService
      }

  internal fun maybeTraceScheduled(executorService: ScheduledExecutorService): ScheduledExecutorService =
      if (tracer != null) {
        TracedScheduledExecutorService(executorService, tracer, /*traceWithActiveSpanOnly=*/false)
      } else {
        executorService
      }

  override fun single(nameFormat: String): ExecutorService {
    checkCreate()
    val threadFactory = threadFactory(nameFormat)
    return maybeTrace(Executors.newSingleThreadExecutor(threadFactory))
        .also { executors[nameFormat] = it }
  }

  override fun fixed(nameFormat: String, threadCount: Int): ExecutorService {
    checkCreate()
    val threadFactory = threadFactory(nameFormat)
    return maybeTrace(Executors.newFixedThreadPool(threadCount, threadFactory))
        .also { executors[nameFormat] = it }
  }

  override fun unbounded(nameFormat: String): ExecutorService {
    checkCreate()
    val threadFactory = threadFactory(nameFormat)
    return maybeTrace(Executors.newCachedThreadPool(threadFactory))
        .also { executors[nameFormat] = it }
  }

  override fun scheduled(nameFormat: String, threadCount: Int): ScheduledExecutorService {
    checkCreate()
    val threadFactory = threadFactory(nameFormat)
    return maybeTraceScheduled(Executors.newScheduledThreadPool(threadCount, threadFactory))
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
