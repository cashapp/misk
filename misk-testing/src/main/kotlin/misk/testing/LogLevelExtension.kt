package misk.testing

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.LoggerFactory
import javax.inject.Inject

/**
 * Helps you set the log level for misk tests. It would default all test by default to Log
 * but you can override it by using the [LogLevel] annotation at the method or class level.
 *
 */
class LogLevelExtension @Inject constructor() : BeforeEachCallback {
  override fun beforeEach(context: ExtensionContext?) {
    val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    root.level =  level(context)
  }

  private fun level(context: ExtensionContext?): Level {
    if(context == null || context.element.isEmpty) {
      return Level.INFO
    }
    return  context.element.get().annotations.firstNotNullOfOrNull { it as? LogLevel }
      ?.let { mapLevel(it.level) }
      ?: findParent(context)
      ?: Level.INFO
  }

  private fun findParent(context: ExtensionContext): Level? {
    return context.requiredTestInstances.allInstances.firstNotNullOfOrNull { instance ->
      instance::class.annotations.firstNotNullOfOrNull { it as? LogLevel }
        ?.let { mapLevel(it.level) }
    }
  }

  private fun mapLevel(level: LogLevel.Level): Level {
    return when (level) {
      LogLevel.Level.INFO -> Level.INFO
      LogLevel.Level.DEBUG -> Level.DEBUG
      LogLevel.Level.WARN -> Level.WARN
      LogLevel.Level.ERROR -> Level.ERROR
    }
  }
}
