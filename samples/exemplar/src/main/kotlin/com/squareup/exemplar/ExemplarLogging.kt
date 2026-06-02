package com.squareup.exemplar

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import org.slf4j.LoggerFactory

object ExemplarLogging {
  private val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
  private val rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)

  fun configure() {
    rootLogger.level = Level.INFO
    installUncaughtExceptionHandler()
  }

  /** Don't dump uncaught exceptions to System.out; format them properly with logging. */
  private fun installUncaughtExceptionHandler() {
    Thread.setDefaultUncaughtExceptionHandler { _, throwable -> rootLogger.error("Uncaught exception!", throwable) }
  }
}
