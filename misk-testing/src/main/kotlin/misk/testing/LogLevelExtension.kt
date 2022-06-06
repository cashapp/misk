package misk.testing

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.LoggerFactory
import javax.inject.Inject

class LogLevelExtension @Inject constructor() : BeforeEachCallback {
  override fun beforeEach(context: ExtensionContext?) {
    // Note that the root logger will be a org.slf4j.Logger, but may not
    // be a ch.qos.logback.class.Logger instance. In particular, it can
    // sometimes be a org.gradle.internal.logging.slf4j.OutputEventListenerBackedLogger
    val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as? Logger
    root?.let { it.level = level(context) }
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
