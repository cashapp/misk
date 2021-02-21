package misk.service

import com.google.common.util.concurrent.AbstractIdleService
import misk.logging.getLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Test services can derive from CachedTestService
 * if they'd like to reuse the same service for the span
 * of a given runtime. This is helpful when you want to avoid
 * incurring the cost of service startup and shutdown with
 * each test run.
 *
 * NOTE: The caching is only useful if the implementing service
 * references a shared instance of their underlying resources.
 * A common way to do this is to leverage a companion object.
 *
 * Example:
 *
 * @Singleton class TestService : CachedTestService() {
 *   override fun actualStartup() {
 *     service.start()
 *   }
 *
 *   override fun actualShutdown() {
 *     service.stop()
 *   }
 *
 *   companion object {
 *     private val service = Service()
 *   }
 * }
 *
 * Sharing the same underlying resources also means that
 * your tests need to be more conscious about accessing
 * resources scoped uniquely to that test run, or else
 * making sure that they cleanup resources before they're run.
 * This is similar to how we work with DBs, where either
 * you run something like truncate tables before your tests,
 * or you ensure that all your test statements are made
 * relative to the scope of your test.
 */
abstract class CachedTestService : AbstractIdleService() {
  final override fun startUp() {
    val serviceBool = hasStartedByService.getOrPut(javaClass.name) { AtomicBoolean() }
    if (serviceBool.compareAndSet(false, true)) {
      log.info { "starting $javaClass.name" }
      actualStartup()
      Runtime.getRuntime().addShutdownHook(
        Thread {
          log.info { "stopping $javaClass.name" }
          actualShutdown()
        }
      )
    } else {
      log.info { "$javaClass.name already running, not starting anything" }
    }
  }

  final override fun shutDown() {
    log.info { "$javaClass.name is cached, will shutdown on runtime shutdown" }
  }

  companion object {
    private val hasStartedByService = ConcurrentHashMap<String, AtomicBoolean>()
    private val log = getLogger<CachedTestService>()
  }

  /** Actually starts the service up. This will be invoked once per runtime. */
  abstract fun actualStartup()

  /** Actually shuts the service down. This will be invoked once per runtime. */
  abstract fun actualShutdown()
}
