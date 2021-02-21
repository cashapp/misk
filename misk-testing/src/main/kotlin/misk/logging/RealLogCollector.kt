package misk.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase
import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service.State.NEW
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import org.slf4j.LoggerFactory

@Singleton
internal class RealLogCollector @Inject constructor() :
  AbstractIdleService(),
  LogCollector,
  LogCollectorService {
  private val queue = LinkedBlockingDeque<ILoggingEvent>()

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
    Thread.sleep(100) // Give the logger some time to flush events.
    val result = mutableListOf<ILoggingEvent>()
    while (queue.isNotEmpty()) {
      val event = takeOrNull(loggerClass, minLevel, pattern)
      if (event != null) result += event
    }
    return result
  }

  override fun takeEvent(loggerClass: KClass<*>?, minLevel: Level, pattern: Regex?): ILoggingEvent {
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
    require(state() != NEW) { "not collecting logs: did you forget to start the service?" }

    val event = queue.poll(500, TimeUnit.MILLISECONDS)
      ?: throw IllegalArgumentException("no events to take!")

    if (loggerClass != null && loggerClass.qualifiedName != event.loggerName) return null
    if (event.level.toInt() < minLevel.toInt()) return null
    if (pattern != null && !pattern.containsMatchIn(event.message.toString())) return null

    return event
  }

  override fun startUp() {
    appender.start()

    val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
    (rootLogger as? Logger)?.addAppender(appender)
  }

  override fun shutDown() {
    val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
    (rootLogger as? Logger)?.detachAppender(appender)

    appender.stop()
  }
}
