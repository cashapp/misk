package wisp.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import kotlin.reflect.KClass

/**
 * Collects log messages, so they may be asserted on for testing.
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
    pattern: Regex? = null,
  ): List<String>

  /**
   * Takes all matching messages, optionally leaving unmatched logs in this collector.
   */
  fun takeMessages(
    loggerClass: KClass<*>? = null,
    minLevel: Level = Level.INFO,
    pattern: Regex? = null,
    consumeUnmatchedLogs: Boolean = false,
  ): List<String>

  /**
   * Waits until a matching event is logged, and returns its message. The returned event and all
   * preceding events are also removed.
   */
  fun takeMessage(
    loggerClass: KClass<*>? = null,
    minLevel: Level = Level.INFO,
    pattern: Regex? = null,
  ): String

  /**
   * Takes the first matching message, optionally leaving unmatched logs in this collector.
   */
  fun takeMessage(
    loggerClass: KClass<*>? = null,
    minLevel: Level = Level.INFO,
    pattern: Regex? = null,
    consumeUnmatchedLogs: Boolean = false,
  ): String

  /**
   * Removes all currently-collected log events and returns those that match the requested criteria.
   */
  fun takeEvents(
    loggerClass: KClass<*>? = null,
    minLevel: Level = Level.INFO,
    pattern: Regex? = null,
    ): List<ILoggingEvent>

  /**
   * Takes all matching events, optionally leaving unmatched logs in this collector.
   */
  fun takeEvents(
    loggerClass: KClass<*>? = null,
    minLevel: Level = Level.INFO,
    pattern: Regex? = null,
    consumeUnmatchedLogs: Boolean = false,
  ): List<ILoggingEvent>

  /**
   * Waits until a matching event is logged, and returns it. The returned event and all preceding
   * events are also removed.
   */
  fun takeEvent(
    loggerClass: KClass<*>? = null,
    minLevel: Level = Level.INFO,
    pattern: Regex? = null,
    ): ILoggingEvent

  /**
   * Take the first matching event, optionally leaving unmatched logs in this collector.
   */
  fun takeEvent(
    loggerClass: KClass<*>? = null,
    minLevel: Level = Level.INFO,
    pattern: Regex? = null,
    consumeUnmatchedLogs: Boolean = false,
  ): ILoggingEvent
}
