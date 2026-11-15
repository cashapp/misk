package misk.logging

/** Adds the given key, value pair to the MDC for the duration of the block. */
inline fun <R> Mdc.withMdc(key: String, value: String, block: () -> R) = withMdc(key to value, block = block)

/** Adds the given tags to the MDC for the duration of the block. */
inline fun <R> Mdc.withMdc(vararg tags: Pair<String, String>, block: () -> R): R {
  val oldState = getCopyOfContextMap()
  return try {
    tags.forEach { (key, value) -> put(key, value) }
    block()
  } finally {
    oldState?.let { setContextMap(it) } ?: clear()
  }
}
