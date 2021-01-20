package misk.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.google.common.util.concurrent.Service
import kotlin.reflect.KClass

/**
 * Collects log messages so they may be asserted on for testing.
 *
 * To use it, add the [LogCollectorModule] to your test. You’ll need `@MiskTest` with services
 * started because this uses a service to install a log appender for the duration of the test.
 *
 * In your test method you should perform some action that logs, then use an injected `logCollector`
 * to verify that the expected logs were emitted:
 *
 * ```
 * @MiskTest
 * class MyTest {
 *   @MiskTestModule
 *   val module = Modules.combine(
 *       MiskServiceModule(),
 *       LogCollectorModule(),
 *       ...
 *   )
 *
 *   @Inject lateinit var logCollector: LogCollector
 *
 *   @Test
 *   fun test() {
 *     someMethodThatLogs()
 *     assertThat(logCollector.takeMessages()).containsExactly("this is the only logged message!")
 *   }
 * }
 * ```
 *
 * Use the optional parameters of [takeMessages] to constrain which log messages are returned.
 */
interface LogCollector {
  /**
   * Removes all currently-collected log messages and returns those that match the requested
   * criteria.
   */
  fun takeMessages(
    loggerClass: KClass<*>? = null,
    minLevel: Level = Level.INFO,
    pattern: Regex? = null
  ): List<String>

  /**
   * Waits until a matching event is logged, and returns its message. The returned event and all
   * preceding events are also removed.
   */
  fun takeMessage(
    loggerClass: KClass<*>? = null,
    minLevel: Level = Level.INFO,
    pattern: Regex? = null
  ): String

  /**
   * Removes all currently-collected log events and returns those that match the requested criteria.
   */
  fun takeEvents(
    loggerClass: KClass<*>? = null,
    minLevel: Level = Level.INFO,
    pattern: Regex? = null
  ): List<ILoggingEvent>

  /**
   * Waits until a matching event is logged, and returns it. The returned event and all preceding
   * events are also removed.
   */
  fun takeEvent(
    loggerClass: KClass<*>? = null,
    minLevel: Level = Level.INFO,
    pattern: Regex? = null
  ): ILoggingEvent
}

/** Marker interface for the service that produces a [LogCollector]. */
interface LogCollectorService : Service
