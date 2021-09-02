package wisp.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

class WispQueuedLogCollector : LogCollector {
  private val queue = LinkedBlockingDeque<ILoggingEvent>()

  private var wasStarted = false

  private val appender = object : UnsynchronizedAppenderBase<ILoggingEvent>() {
    override fun append(event: ILoggingEvent) {
      queue.put(event)
    }
  }

  override fun takeMessages(
    loggerClass: KClass<*>?,
    minLevel: Level,
    pattern: Regex?,
    consumeUnmatchedLogs: Boolean,
  ): List<String> = takeEvents(
    loggerClass,
    minLevel,
    pattern,
    consumeUnmatchedLogs
  ).map { it.message }

  override fun takeMessage(
    loggerClass: KClass<*>?,
    minLevel: Level,
    pattern: Regex?,
    consumeUnmatchedLogs: Boolean,
  ): String = takeEvent(loggerClass, minLevel, pattern, consumeUnmatchedLogs).message

  override fun takeEvents(
    loggerClass: KClass<*>?,
    minLevel: Level,
    pattern: Regex?,
    consumeUnmatchedLogs: Boolean,
  ): List<ILoggingEvent> {
    sleep(100) // Give the logger some time to flush events.
    if (!consumeUnmatchedLogs) {
      return takeEvents(loggerClass, minLevel, pattern)
    }
    return takeEventsConsuming(loggerClass, minLevel, pattern)
  }

  /**
   * Takes all events currently on the queue which match the constraints.
   * Leaves behind events that don't match.
   */
  private fun takeEvents(
    loggerClass: KClass<*>?,
    minLevel: Level,
    pattern: Regex?
  ): List<ILoggingEvent> {
    val resultList = queue.filter { matchLog(it, loggerClass, minLevel, pattern) }.toList()
    queue.removeAll(resultList)
    return resultList
  }

  /**
   * Consumes the whole queue of events, returning only those which match the constraints.
   */
  private fun takeEventsConsuming(
    loggerClass: KClass<*>?,
    minLevel: Level,
    pattern: Regex?
  ): MutableList<ILoggingEvent> {
    val result = mutableListOf<ILoggingEvent>()
    while (queue.isNotEmpty()) {
      val event = takeOrNull(loggerClass, minLevel, pattern)
      if (event != null) result += event
    }
    return result
  }

  override fun takeEvent(
    loggerClass: KClass<*>?,
    minLevel: Level,
    pattern: Regex?,
    consumeUnmatchedLogs: Boolean,
  ): ILoggingEvent {
    require(wasStarted) { "not collecting logs: did you forget to start the service?" }
    if (!consumeUnmatchedLogs) {
      return take(loggerClass, minLevel, pattern)
    }
    return takeConsuming(loggerClass, minLevel, pattern)
  }

  /**
   * Takes an event matching the constraints, leaving behind all other non-matching events
   * in the queue. Throws if there are no matching events.
   */
  private fun take(
    loggerClass: KClass<*>?,
    minLevel: Level,
    pattern: Regex?
  ): ILoggingEvent {
    for (i in 1..5) {
      if (queue.isEmpty()) sleep(100) else continue
    }
    require(queue.isNotEmpty()) { "no events to take!" }
    val event = queue.find { matchLog(it, loggerClass, minLevel, pattern) }
      ?: error("no matching events for (logger=$loggerClass, minLevel=$minLevel, pattern=$pattern)")
    queue.remove(event)
    return event
  }

  /**
   * Like [take], but consumes all events in the queue preceding the first match.
   * Waits forever until there is a matching event.
   */
  private fun takeConsuming(
    loggerClass: KClass<*>?,
    minLevel: Level,
    pattern: Regex?
  ): ILoggingEvent {
    while (true) {
      val event = takeOrNull(loggerClass, minLevel, pattern)
      if (event != null) return event
    }
  }

  /** Takes an event. Returns it if it meets the constraints and null if it doesn't. */
  private fun takeOrNull(
    loggerClass: KClass<*>?,
    minLevel: Level,
    pattern: Regex?
  ): ILoggingEvent? {
    require(wasStarted) { "not collecting logs: did you forget to start the service?" }

    val event = queue.poll(500, TimeUnit.MILLISECONDS)
      ?: throw IllegalArgumentException("no events to take!")

    return if (matchLog(event, loggerClass, minLevel, pattern)) event else null
  }

  private fun matchLog(
    event: ILoggingEvent,
    loggerClass: KClass<*>?,
    minLevel: Level,
    pattern: Regex?
  ) = when {
    loggerClass != null && loggerClass.qualifiedName != event.loggerName -> false
    event.level.toInt() < minLevel.toInt() -> false
    pattern != null && !pattern.containsMatchIn(event.message.toString()) -> false
    else -> true
  }

  fun startUp() {
    appender.start()
    wasStarted = true

    val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
    (rootLogger as? Logger)?.addAppender(appender)
  }

  fun shutDown() {
    val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
    (rootLogger as? Logger)?.detachAppender(appender)

    appender.stop()
  }
}
