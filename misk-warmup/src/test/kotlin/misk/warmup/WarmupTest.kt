package misk.warmup

import ch.qos.logback.classic.Level
import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.Module
import com.google.inject.Provides
import misk.MiskTestingServiceModule
import misk.ServiceModule
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.logging.LogCollectorModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.logging.LogCollector
import java.time.Duration
import java.util.concurrent.BlockingDeque
import java.util.concurrent.LinkedBlockingDeque
import javax.inject.Inject
import javax.inject.Singleton

internal class WarmupTest {
  private val events = LinkedBlockingDeque<String>()
  private lateinit var serviceManager: ServiceManager
  private lateinit var healthChecks: List<HealthCheck>
  private lateinit var logCollector: LogCollector

  @Test
  fun `happy path`() {
    startUpAndShutDown(
      ServiceModule<LoggingService>(),
      WarmupModule<LoggingWarmupTask>(),
    )

    assertThat(events).containsExactly(
      "LoggingService startUp",
      "LoggingWarmupTask created on warmup-0",
      "LoggingWarmupTask warming on warmup-0",
      "HealthChecks all passed",
      "LoggingService shutDown",
    )

    assertThat(logCollector.takeMessage(minLevel = Level.INFO))
      .isEqualTo("Running warmup tasks: [LoggingWarmupTask]")
    assertThat(logCollector.takeMessage(minLevel = Level.INFO))
      .startsWith("Warmup task LoggingWarmupTask completed")
  }

  @Test
  fun `service is healthy even after warmup task throws`() {
    startUpAndShutDown(
      ServiceModule<LoggingService>(),
      WarmupModule<ThrowingWarmupTask>(),
    )

    assertThat(events).containsExactly(
      "LoggingService startUp",
      "ThrowingWarmupTask created on warmup-0",
      "ThrowingWarmupTask about to crash on warmup-0",
      "HealthChecks all passed",
      "LoggingService shutDown",
    )

    assertThat(logCollector.takeMessage(minLevel = Level.INFO))
      .isEqualTo("Running warmup tasks: [ThrowingWarmupTask]")
    assertThat(logCollector.takeMessage(minLevel = Level.ERROR))
      .startsWith("Warmup task ThrowingWarmupTask crashed")
  }

  @Singleton
  class LoggingService @Inject constructor(
    private val events: BlockingDeque<String>
  ) : AbstractIdleService() {

    override fun startUp() {
      events += "LoggingService startUp"
    }

    override fun shutDown() {
      events += "LoggingService shutDown"
    }
  }

  @Singleton
  class LoggingWarmupTask @Inject constructor(
    private val events: BlockingDeque<String>
  ) : WarmupTask() {
    init {
      events += "LoggingWarmupTask created on ${Thread.currentThread().name}"
    }

    override fun execute() {
      events += "LoggingWarmupTask warming on ${Thread.currentThread().name}"
    }
  }

  @Singleton
  class ThrowingWarmupTask @Inject constructor(
    private val events: BlockingDeque<String>
  ) : WarmupTask() {
    init {
      events += "ThrowingWarmupTask created on ${Thread.currentThread().name}"
    }

    override fun execute() {
      events += "ThrowingWarmupTask about to crash on ${Thread.currentThread().name}"
      throw RuntimeException("boom")
    }
  }

  /**
   * This test doesn't use `@MiskTest` so we can start up and shut down the injector in the test.
   * It also customizes the modules per-test.
   */
  private fun startUpAndShutDown(vararg modules: Module) {
    val injector = Guice.createInjector(
      object : KAbstractModule() {
        override fun configure() {
          install(MiskTestingServiceModule())
          install(LogCollectorModule())
          for (module in modules) {
            install(module)
          }
        }

        @Provides fun provideEvents(): BlockingDeque<String> = events
      }
    )

    logCollector = injector.getInstance()

    serviceManager = injector.getInstance()
    serviceManager.startAsync()
    serviceManager.awaitHealthy()

    healthChecks = injector.getInstance(object : Key<List<HealthCheck>>() {})

    healthChecks.await {
      all { it.status().isHealthy }
    }
    events += "HealthChecks all passed"

    serviceManager.stopAsync()
    serviceManager.awaitStopped()
  }

  private fun <T> T.await(
    timeout: Duration = Duration.ofSeconds(10L),
    sleep: Duration = Duration.ofMillis(20L),
    condition: T.() -> Boolean
  ) {
    val stopwatch = Stopwatch.createStarted()
    while (stopwatch.elapsed() < timeout) {
      if (condition()) {
        return
      }
      Thread.sleep(sleep.toMillis())
    }
    throw RuntimeException("condition not met after $timeout")
  }
}
