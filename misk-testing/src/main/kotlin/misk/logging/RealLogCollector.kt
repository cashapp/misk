package misk.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase
import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service.State.NEW
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
internal class RealLogCollector @Inject constructor(): AbstractIdleService(), LogCollector {
  private val events = mutableListOf<ILoggingEvent>()

  private val appender = object : UnsynchronizedAppenderBase<ILoggingEvent>() {
    override fun append(event: ILoggingEvent) {
      synchronized(this@RealLogCollector) {
        events.add(event)
      }
    }
  }

  override fun takeMessages(
    loggerClass: KClass<*>?,
    minLevel: Level,
    pattern: Regex?
  ): List<String> {
    return takeEvents(loggerClass, minLevel, pattern).map { it.message.toString() }
  }

  override fun takeEvents(
    loggerClass: KClass<*>?,
    minLevel: Level,
    pattern: Regex?
  ): List<ILoggingEvent> {
    require(state() != NEW) { "not collecting logs: did you forget to start the service?" }

    synchronized(this@RealLogCollector) {
      val result = events.filter {
        (loggerClass == null || loggerClass.qualifiedName == it.loggerName) &&
            it.level.toInt() >= minLevel.toInt() &&
            (pattern == null || pattern.containsMatchIn(it.message.toString()))
      }

      events.clear()

      return result
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