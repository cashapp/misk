package misk.logging

import mu.KLogger
import mu.KotlinLogging
import org.slf4j.event.Level

inline fun <reified T> getLogger(): KLogger {
  return KotlinLogging.logger(T::class.qualifiedName!!)
}

fun KLogger.log(
    level: Level,
    message: String
) {
  when (level) {
    Level.ERROR -> error(message)
    Level.WARN -> warn(message)
    Level.INFO -> info(message)
    Level.DEBUG -> debug(message)
    Level.TRACE -> trace(message)
  }
}

fun KLogger.log(
    level: Level,
    th: Throwable,
    message: () -> Any?
) {
  when (level) {
    Level.ERROR -> error(th, message)
    Level.INFO -> info(th, message)
    Level.WARN -> warn(th, message)
    Level.DEBUG -> debug(th, message)
    Level.TRACE -> trace(th, message)
  }
}
