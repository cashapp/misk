package wisp.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.util.concurrent.LinkedBlockingDeque
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
    pattern: Regex?
  ): List<String> = takeEvents(loggerClass, minLevel, pattern).map { it.message }

  override fun takeMessage(
    loggerClass: KClass<*>?,
    minLevel: Level,
    pattern: Regex?
  ): String = takeEvent(loggerClass, minLevel, pattern).message

  override fun takeEvents(
    loggerClass: KClass<*>?,
    minLevel: Level,
    pattern: Regex?
  ): List<ILoggingEvent> {
    sleep(100) // Give the logger some time to flush events.
    val resultList = queue.filter { matchLog(loggerClass, it, minLevel, pattern) }.toList()
    queue.removeAll(resultList)
    return resultList
  }

  override fun takeEvent(loggerClass: KClass<*>?, minLevel: Level, pattern: Regex?): ILoggingEvent {
    require(wasStarted) { "not collecting logs: did you forget to start the service?" }
    for (i in 1..5) {
      if (queue.isEmpty()) sleep(100) else continue
    }
    require(queue.isNotEmpty()) { "no events to take!" }
    val event = queue.find { matchLog(loggerClass, it, minLevel, pattern) }
      ?: error("no matching events for (logger=$loggerClass, minLevel=$minLevel, pattern=$pattern)")
    queue.remove(event)
    return event
  }

  private fun matchLog(
    loggerClass: KClass<*>?,
    event: ILoggingEvent,
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
