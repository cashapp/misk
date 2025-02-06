package misk.logging.coroutines

import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import misk.logging.Mdc
import mu.KotlinLogging
import wisp.logging.getLogger
import kotlin.coroutines.coroutineContext

/**
 * Adds the given key, value pair to the MDC for the duration of the block.
 * This is coroutine safe, so the additions will be added to the coroutine context
 */
suspend inline fun Mdc.withMdc(key: String, value: String, crossinline block: suspend () -> Unit) =
  withMdc(key to value, block = block)

/**
 * Adds the given tags to the MDC for the duration of the block.
 * This is coroutine safe, so the additions will be added to the coroutine context
 */
suspend inline fun Mdc.withMdc(
  vararg tags: Pair<String, String>,
  crossinline block: suspend () -> Unit
) {
  if(coroutineContext[MDCContext] == null) {
    KotlinLogging.logger("misk.logging.coroutines.ScopedMdc").warn {
      "MDCContext is not present in the coroutine context, this is required to restore the previous MDC state"
    }
  }
  tags.forEach { (key, value) -> put(key, value) }
  return withContext(MDCContext()) {
    block()
  }
}

