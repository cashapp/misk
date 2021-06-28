package misk.warmup

import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.ServiceManager
import misk.concurrent.ExecutorServiceFactory
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import wisp.logging.getLogger
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * This class is a health check to defer production traffic until all warmup tasks have completed.
 *
 * Once the [ServiceManager] reports that all services have started, it runs all warmup tasks in
 * parallel. (It doesn't start earlier so warmup tasks have access to all services when they are
 * created).
 *
 * Note that if a warmup task fails by throwing an exception, that is not fatal. This will report
 * itself as healthy, and early call latency might not be as low as it should be.
 */
@Singleton
internal class WarmupRunner @Inject constructor(
  private val executorServiceFactory: ExecutorServiceFactory,
  /** Inject providers because task dependencies won't be ready when this runner is created. */
  private val taskProviders: Map<String, @JvmSuppressWildcards Provider<WarmupTask>>
) : HealthCheck, ServiceManager.Listener() {
  private val warmingUpCount = AtomicInteger(taskProviders.size)

  override fun healthy() {
    val executorService = executorServiceFactory.fixed("warmup-%d", warmingUpCount.get())
    logger.info { "Running warmup tasks: ${taskProviders.keys}" }
    for ((name, taskProvider) in taskProviders) {
      executorService.submit {
        val stopwatch = Stopwatch.createStarted()
        try {
          val task = taskProvider.get()
          task.execute()
          logger.info { "Warmup task $name completed after $stopwatch" }
        } catch (t: Throwable) {
          logger.error(t) { "Warmup task $name crashed after $stopwatch" }
        } finally {
          warmingUpCount.decrementAndGet()
        }
      }
    }
    executorService.shutdown()
  }

  override fun status(): HealthStatus {
    return when (val count = warmingUpCount.get()) {
      0 -> HealthStatus.healthy("all ${taskProviders.size} warmed up")
      else -> HealthStatus.unhealthy("$count/${taskProviders.size} warming up")
    }
  }

  companion object {
    private val logger = getLogger<WarmupRunner>()
  }
}
