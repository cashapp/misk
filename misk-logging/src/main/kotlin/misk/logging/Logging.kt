package misk.logging

import misk.sampling.Sampler
import mu.KLogger
import mu.KotlinLogging
import org.slf4j.MDC
import org.slf4j.event.Level

typealias Tag = Pair<String, Any?>

inline fun <reified T> getLogger(): KLogger {
  return KotlinLogging.logger(T::class.qualifiedName!!)
}

/**
 * Returns a logger that samples logs. This logger MUST be instantiated statically, in a companion object or as a
 * Singleton.
 *
 * To get a rate limited logger:
 *
 * val logger = getLogger<MyClass>().sampled(RateLimitingSampler(RATE_PER_SECOND))
 *
 * To get a probabilistic sampler
 *
 * val logger = getLogger<MyClass>().sampled(PercentSampler(PERCENTAGE_TO_ALLOW))
 */
fun KLogger.sampled(sampler: Sampler = Sampler.rateLimiting(1L)): KLogger {
  return SampledLogger(this, sampler)
}

fun KLogger.info(vararg tags: Tag, message: () -> Any?) = log(Level.INFO, message = message, tags = tags)

fun KLogger.warn(vararg tags: Tag, message: () -> Any?) = log(Level.WARN, message = message, tags = tags)

fun KLogger.error(vararg tags: Tag, message: () -> Any?) = log(Level.ERROR, message = message, tags = tags)

fun KLogger.debug(vararg tags: Tag, message: () -> Any?) = log(Level.DEBUG, message = message, tags = tags)

fun KLogger.trace(vararg tags: Tag, message: () -> Any?) = log(Level.TRACE, message = message, tags = tags)

fun KLogger.info(th: Throwable, vararg tags: Tag, message: () -> Any?) =
  log(Level.INFO, th, message = message, tags = tags)

fun KLogger.warn(th: Throwable, vararg tags: Tag, message: () -> Any?) =
  log(Level.WARN, th, message = message, tags = tags)

fun KLogger.error(th: Throwable, vararg tags: Tag, message: () -> Any?) =
  log(Level.ERROR, th, message = message, tags = tags)

fun KLogger.debug(th: Throwable, vararg tags: Tag, message: () -> Any?) =
  log(Level.DEBUG, th, message = message, tags = tags)

fun KLogger.trace(th: Throwable, vararg tags: Tag, message: () -> Any?) =
  log(Level.TRACE, th, message = message, tags = tags)

// This logger takes care of adding the mdc tags and cleaning them up when done
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

// This logger takes care of adding the mdc tags and cleaning them up when done
fun KLogger.log(level: Level, th: Throwable?, vararg tags: Tag, message: () -> Any?) {
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

fun <T> withTags(vararg tags: Tag, block: () -> T): T {
  // Establish MDC, saving prior MDC
  val priorMDC =
    tags.map { (key, value) ->
      val priorValue = MDC.get(key)
      MDC.put(key, value.toString())
      key to priorValue
    }

  try {
    return block()
  } finally {
    // Restore or clear prior MDC
    priorMDC.forEach { (key, value) -> if (value == null) MDC.remove(key) else MDC.put(key, value) }
  }
}

/** `includeTagsOnExceptionLogs`: For usage instructions, please see docs below on `withSmartTags` */
fun <T> withTags(vararg tags: Tag, includeTagsOnExceptionLogs: Boolean = false, block: () -> T): T {
  return withTags(*tags) {
    if (includeTagsOnExceptionLogs) {
      SmartTagsThreadLocalHandler.includeTagsOnExceptions(*tags, block = block)
    } else {
      block()
    }
  }
}

/**
 * Use this function to add tags to the MDC context for the duration of the block.
 *
 * This is particularly useful (the smart aspect) when an exception is thrown within the block, the tags can be
 * retrieved outside that block using `SmartTagsThreadLocalHandler.popThreadLocalSmartTags()` and added to the MDC
 * context again when logging the exception.
 *
 * Within Misk this is already built into both WebAction (`misk.web.exceptions.ExceptionHandlingInterceptor`) and
 * `misk.jobqueue.sqs.SqsJobConsumer`. These can be used as an example to extend for any other incoming "event"
 * consumers within a service such as Kafka, scheduled tasks, temporal workflows, etc.
 *
 * Usage:
 * ```
 * class ServiceAction (private val webClient: WebClient): WebAction {
 *
 *   @Post("/api/resource")
 *   fun executeWebAction(@RequestBody request: ServiceActionRequest) {
 *     logger.info() { "Received request" }
 *
 *     val loadedContext = aClient.load(request.id)
 *
 *     withSmartTags(
 *       "processValue" to request.process_value,
 *       "contextToken" to loadedContext.token
 *     ) {
 *       logger.info() { "Processing request" }
 *       doSomething()
 *     }
 *   }
 *
 *   private fun doSomething() {
 *     logger.info() { "Start Process" }
 *
 *     client.someWebRequest() // Client throws exception which is caught and logged by misk framework
 *
 *     logger.info() { "Done" }
 *   }
 *
 *   companion object {
 *     val logger = KotlinLogging.logger(ServiceAction::class.java.canonicalName)
 *   }
 * }
 * ```
 *
 * Logging result:
 * ```
 *   Log MDC context: [] Log message: "Received request"
 *   Log MDC context: [processValue: "PV_123", contextToken: "contextTokenValue"] Log message: "Processing request"
 *   Log MDC context: [processValue: "PV_123", contextToken: "contextTokenValue"] Log message: "Start Process"
 *   Log MDC context: [processValue: "PV_123", contextToken: "contextTokenValue"] Log message: "unexpected error dispatching to ServiceAction" // This log would not normally include the MDC context
 * ```
 */
fun <T> withSmartTags(vararg tags: Tag, block: () -> T): T {
  return withTags(*tags, includeTagsOnExceptionLogs = true, block = block)
}
