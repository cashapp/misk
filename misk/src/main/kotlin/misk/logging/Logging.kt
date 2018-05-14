package misk.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.filter.ThresholdFilter
import com.google.cloud.logging.TraceLoggingEnhancer
import com.google.cloud.logging.logback.LoggingAppender
import misk.cloud.gcp.logging.StackDriverLoggingConfig
import misk.cloud.gcp.tracing.TracingLoggingEnhancer
import mu.KLogger
import mu.KotlinLogging
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

inline fun <reified T> getLogger(): KLogger {
  return KotlinLogging.logger(T::class.qualifiedName!!)
}

/**
 * Configures Misk to send application logs to StackDriver. If credentials are
 * required to send logging, set the GOOGLE_APPLICATION_CREDENTIALS environment
 * variable with the path to the JSON credentials.
 */
fun enableStackDriverLogging(config: StackDriverLoggingConfig) {
  val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
  val context = rootLogger.loggerContext
  context.reset()

  val filter = ThresholdFilter()
  filter.setLevel(config.filter_level.levelStr)
  filter.context = context
  filter.start()

  val appender = LoggingAppender()
  appender.addFilter(filter)
  appender.addEnhancer(TracingLoggingEnhancer::class.qualifiedName)
  appender.setFlushLevel(config.flush_level)
  appender.setLog(config.log)
  appender.context = context
  appender.start()

  rootLogger.addAppender(appender)
}

fun KLogger.log(level: Level, message: String) {
  when (level) {
    Level.ERROR -> error(message)
    Level.WARN -> warn(message)
    Level.INFO -> info(message)
    Level.DEBUG -> debug(message)
    Level.TRACE -> trace(message)
  }
}

fun KLogger.log(level: Level, th: Throwable, message: () -> Any?) {
  when (level) {
    Level.ERROR -> error(th, message)
    Level.INFO -> info(th, message)
    Level.WARN -> warn(th, message)
    Level.DEBUG -> debug(th, message)
    Level.TRACE -> trace(th, message)
  }
}
