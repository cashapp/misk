package misk.web.jetty

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import org.eclipse.jetty.util.thread.ThreadPool

@Singleton
internal class JettyThreadPoolHealthCheck @Inject constructor(private val threadPool: ThreadPool) : HealthCheck {

  override fun status(): HealthStatus {
    return when (threadPool.isLowOnThreads()) {
      true -> HealthStatus.unhealthy("Jetty threadpool low on threads: $threadPool")
      else -> HealthStatus.healthy("Jetty threadpool healthy: $threadPool")
    }
  }
}
