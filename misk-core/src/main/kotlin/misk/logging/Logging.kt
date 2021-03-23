package misk.logging

import misk.sampling.Sampler
import mu.KLogger
import org.slf4j.MDC
import org.slf4j.event.Level

@Deprecated(
  "Use wisp-logging",
  ReplaceWith(
    "Tag",
    "wisp.logging.Tag",
  )
)
typealias Tag = Pair<String, Any?>

@Deprecated(
  "Use wisp-logging",
  ReplaceWith(
    "getLogger<T>()",
    "wisp.logging.getLogger",
  )
)
inline fun <reified T> getLogger(): KLogger {
  return wisp.logging.getLogger<T>()
}

/**
 * Returns a logger that samples logs. This logger MUST be instantiated statically,
 * in a companion object or as a Singleton.
 *
 * To get a rate limited logger:
 *
 *   val logger = getLogger<MyClass>().sampled(RateLimitingSampler(RATE_PER_SECOND))
 *
 * To get a probabilistic sampler
 *
 *   val logger = getLogger<MyClass>().sampled(PercentSampler(PERCENTAGE_TO_ALLOW))
 */
fun KLogger.sampled(sampler: Sampler): KLogger {
  return SampledLogger(this, sampler)
}

@Deprecated(
  "Use wisp-logging",
  ReplaceWith(
    "info(tags, message)",
    "wisp.logging.info",
  )
)
fun KLogger.info(vararg tags: Tag, message: () -> Any?) =
  log(Level.INFO, message = message, tags = tags)

@Deprecated(
  "Use wisp-logging",
  ReplaceWith(
    "warn(tags, message)",
    "wisp.logging.warn",
  )
)
fun KLogger.warn(vararg tags: Tag, message: () -> Any?) =
  log(Level.WARN, message = message, tags = tags)

@Deprecated(
  "Use wisp-logging",
  ReplaceWith(
    "error(tags, message)",
    "wisp.logging.error",
  )
)
fun KLogger.error(vararg tags: Tag, message: () -> Any?) =
  log(Level.ERROR, message = message, tags = tags)

@Deprecated(
  "Use wisp-logging",
  ReplaceWith(
    "debug(tags, message)",
    "wisp.logging.debug",
  )
)
fun KLogger.debug(vararg tags: Tag, message: () -> Any?) =
  log(Level.DEBUG, message = message, tags = tags)

@Deprecated(
  "Use wisp-logging",
  ReplaceWith(
    "info(th, tags, message)",
    "wisp.logging.info",
  )
)
fun KLogger.info(th: Throwable, vararg tags: Tag, message: () -> Any?) =
  log(Level.INFO, th, message = message, tags = tags)

@Deprecated(
  "Use wisp-logging",
  ReplaceWith(
    "warn(th, tags, message)",
    "wisp.logging.warn",
  )
)
fun KLogger.warn(th: Throwable, vararg tags: Tag, message: () -> Any?) =
  log(Level.WARN, th, message = message, tags = tags)

@Deprecated(
  "Use wisp-logging",
  ReplaceWith(
    "error(th, tags, message)",
    "wisp.logging.error",
  )
)
fun KLogger.error(th: Throwable, vararg tags: Tag, message: () -> Any?) =
  log(Level.ERROR, th, message = message, tags = tags)

@Deprecated(
  "Use wisp-logging",
  ReplaceWith(
    "debug(th, tags, message)",
    "wisp.logging.debug",
  )
)
fun KLogger.debug(th: Throwable, vararg tags: Tag, message: () -> Any?) =
  log(Level.DEBUG, th, message = message, tags = tags)

@Deprecated(
  "Use wisp-logging",
  ReplaceWith(
    "log(level, tags, message)",
    "wisp.logging.log",
  )
)
fun KLogger.log(level: Level, vararg tags: Tag, message: () -> Any?) {
  withTags(*tags) {
    when (level) {
      Level.ERROR -> error(message)
      Level.WARN -> warn(message)
      Level.INFO -> info(message)
      Level.DEBUG -> debug(message)
      Level.TRACE -> trace(message)
    }
  }
}

@Deprecated(
  "Use wisp-logging",
  ReplaceWith(
    "log(level, th, tags, message)",
    "wisp.logging.log",
  )
)
fun KLogger.log(level: Level, th: Throwable, vararg tags: Tag, message: () -> Any?) {
  withTags(*tags) {
    when (level) {
      Level.ERROR -> error(th, message)
      Level.INFO -> info(th, message)
      Level.WARN -> warn(th, message)
      Level.DEBUG -> debug(th, message)
      Level.TRACE -> trace(th, message)
    }
  }
}

private fun withTags(vararg tags: Tag, f: () -> Unit) {
  // Establish MDC, saving prior MDC
  val priorMDC = tags.map { (k, v) ->
    val priorValue = MDC.get(k)
    MDC.put(k, v.toString())
    k to priorValue
  }

  try {
    f()
  } finally {
    // Restore or clear prior MDC
    priorMDC.forEach { (k, v) -> if (v == null) MDC.remove(k) else MDC.put(k, v) }
  }
}
