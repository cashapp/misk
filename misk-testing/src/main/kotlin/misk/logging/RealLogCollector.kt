package misk.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase
import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service.State.NEW
import org.slf4j.LoggerFactory
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
internal class RealLogCollector @Inject constructor() : AbstractIdleService(), LogCollector {
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
  ): List<String> = takeEvents(loggerClass, minLevel, pattern).map { it.message.toString() }

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
    val result = mutableListOf<ILoggingEvent>()
    while (queue.isNotEmpty()) {
      result += takeEvent(loggerClass, minLevel, pattern)
    }
    return result
  }

  override fun takeEvent(loggerClass: KClass<*>?, minLevel: Level, pattern: Regex?): ILoggingEvent {
    require(state() != NEW) { "not collecting logs: did you forget to start the service?" }

    while (true) {
      val event = queue.poll(500, TimeUnit.MILLISECONDS)
          ?: throw IllegalArgumentException("no events to take!")

      if ((loggerClass == null || loggerClass.qualifiedName == event.loggerName) &&
          event.level.toInt() >= minLevel.toInt() &&
          (pattern == null || pattern.containsMatchIn(event.message.toString()))) {
        return event
      }
    }
  }

  override fun startUp() {
    appender.start()
    (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).addAppender(appender)
  }

  override fun shutDown() {
    (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).detachAppender(appender)
    appender.stop()
  }
}