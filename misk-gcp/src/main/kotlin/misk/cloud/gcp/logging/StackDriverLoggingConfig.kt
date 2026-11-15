package misk.cloud.gcp.logging

import ch.qos.logback.classic.Level
import misk.config.Config

data class StackDriverLoggingConfig
@JvmOverloads
constructor(
  val flush_level: Level = Level.ERROR,
  val filter_level: Level = Level.INFO,
  // No default here as LoggingAppender attempts to auto-detect with "global" as a fallback
  val resource_type: String?,
  val log: String = "java.log",
) : Config
