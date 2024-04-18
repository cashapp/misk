package wisp.logging

import mu.KLogger
import mu.KotlinLogging
import org.slf4j.MDC
import kotlin.reflect.KClass

/**
 * This is a logging class to help apply and remove MDC context tags from within calls in service code.
 *
 * In particular, it solves the problem with searching logs using the MDC context tags where an exception
 * caught and thrown by misk doesn't include the MDC tags and doesn't show up in the search. Using this
 * will mean exceptions will be visible in the sequence of logs relating to a tag. See the logging
 * example below for usage and the logging output.
 *
 *
 * Usage:
 *
 * First set up a logger class with relevant MDC functions for the code base:
 *
 * class MyServiceLogger<T: Any>(loggerClass: KClass<T>): TaggedLogger<T, MyServiceLogger<T>>(loggerClass) {
 *   fun processValue(value: String?) = tag("process_value" to value)
 * }
 *
 * // Create a global helper function to return the above class
 * // Can be called from companion objects or regular classes - will find correct logger
 * fun <T : Any> KClass<T>.getTaggedLogger(): MyServiceLogger<T> {
 *   return MyServiceLogger(this)
 * }
 *
 *
 * Then to use the tagged logger for example:
 *
 * class ServiceAction (private val webClient: WebClient): WebAction {
 *
 *   @Post("/api/resource")
 *   fun executeWebAction(@RequestBody request: ServiceActionRequest) {
 *     logger
 *       .processValue(request.process_value)
 *       .asContext() {
 *         logger.info() { "Received request" }
 *         doSomething()
 *       }
 *   }
 *
 *   private fun doSomething() {
 *     logger.info() { "Start Process" }
 *
 *     client.someWebRequest() // Client throws exception to be caught and logged by misk framework
 *
 *     logger.info() { "Done" }
 *   }
 *
 *   companion object {
 *     val logger = this::class.getTaggedLogger()
 *   }
 * }
 *
 *
 * Logging result:
 *   Log MDC context: [process_value: PV_123] Log message: "Received request"
 *   Log MDC context: [process_value: PV_123] Log message: "Start Process"
 *   Log MDC context: [process_value: PV_123] Log message: "unexpected error dispatching to ServiceAction" // This log would not normally include the MDC context
 *
 */

open class TaggedLogger<L:Any, out R: TaggedLogger<L, R>> private constructor(
  private val kLogger: KLogger,
  private val tags: MutableSet<Tag>
): KLogger by kLogger {

  constructor(loggerClass: KClass<L>, tags: Set<Tag> = emptySet()) : this(
    getLogger(loggerClass),
    tags.toMutableSet()
  )

  // Add tags to the list of MDC tags for the current logger in context, including any other nested TaggedLoggers
  fun tag(vararg newTags: Tag): R {
    tags.addAll(newTags)
    @Suppress("UNCHECKED_CAST")
    return this as R
  }

  // Adds the tags to the Mapped Diagnostic Context for the current thread for the duration of the block.
  fun <T> asContext(f: () -> T): T {
    val priorMDC = MDC.getCopyOfContextMap()

    tags.forEach { (k, v) ->
      if (v != null) {
        MDC.put(k, v.toString())
      }
    }

    try {
      return f().also {
        // Exiting this TaggedLogger gracefully: Lets do some cleanup to keep the ThreadLocal clear.
        // The scenario here is that a nested TaggedLogger threw an exception, it was
        // caught and handled by this TaggedLogger, so it should clean up the unused context.
        threadLocalMdcContext.remove()
      }
    } catch (th: Throwable) {
      // TaggedLoggers can be nested - only set if there is not already a context set
      // This will be cleared upon logging of the exception within misk or if the thrown exception
      // is handled by a higher level TaggedLogger
      if (shouldSetThreadLocalContext(th)) {
        // Set thread local MDC context for the ExceptionHandlingInterceptor to read
        threadLocalMdcContext.set(ThreadLocalTaggedLoggerMdcContext.createWithMdcSnapshot(th))
      }
      throw th
    } finally {
      MDC.setContextMap(priorMDC ?: emptyMap())
    }
  }

  private fun shouldSetThreadLocalContext(th: Throwable): Boolean {
    // This is the first of any nested TaggedLoggers to catch this exception
    if (threadLocalMdcContext.get() == null) {
      return true
    }

    // A nested TaggedLogger may have caught and handled the exception, and has now thrown something else
    return !(threadLocalMdcContext.get()?.wasTriggeredBy(th) ?: false)
  }

  private data class ThreadLocalTaggedLoggerMdcContext(
    val triggeringThrowable: Throwable,
    val tags: Set<Tag>
  ) {
    fun wasTriggeredBy(throwable: Throwable): Boolean {
      return triggeringThrowable == throwable
    }

    companion object {
      fun createWithMdcSnapshot(triggeringThrowable: Throwable) =
        ThreadLocalTaggedLoggerMdcContext(triggeringThrowable, MDC.getCopyOfContextMap().map { Tag(it.key, it.value) }.toSet())
    }
  }

  companion object {
    private val threadLocalMdcContext = ThreadLocal<ThreadLocalTaggedLoggerMdcContext>()

    fun popThreadLocalMdcContext() = threadLocalMdcContext
      .get()
      ?.tags
      ?.also { threadLocalMdcContext.remove() }
      ?: emptySet()

    private fun <T : Any> getLogger(loggerClass: KClass<T>): KLogger {
      return when {
        loggerClass.isCompanion -> {
          KotlinLogging.logger(loggerClass.java.declaringClass.canonicalName)
        }

        else -> {
          KotlinLogging.logger(loggerClass.java.canonicalName)
        }
      }
    }
  }
}
