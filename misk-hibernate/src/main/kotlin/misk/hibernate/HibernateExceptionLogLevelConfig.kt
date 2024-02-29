package misk.hibernate

import org.slf4j.event.Level
import wisp.config.Config
import javax.inject.Singleton

/**
 * Configures the log [Level] for a HibernateException.
 *
 * @property log_level
 */

@Singleton
data class HibernateExceptionLogLevelConfig @JvmOverloads constructor(
  val log_level: Level = Level.WARN
) : Config
