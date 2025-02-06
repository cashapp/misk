package misk.logging

import org.slf4j.MDC

/**
 * Adds the given key, value pair to the MDC for the duration of the block.
 */
inline fun Mdc.withMdc(key: String, value: String, block: () -> Unit) =
  withMdc(key to value, block = block)

/**
 * Adds the given tags to the MDC for the duration of the block.
 */
inline fun Mdc.withMdc(vararg tags: Pair<String, String>, block: () -> Unit) {
  val oldState = getCopyOfContextMap()
  return try {
    tags.forEach { (key, value) -> put(key, value) }
    block()
  } finally {
    oldState?.let { setContextMap(it) } ?: clear()
  }
}

