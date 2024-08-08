package wisp.logging

object SmartTagsThreadLocalHandler {
  private val threadLocalMdcContext = ThreadLocal<ThreadLocalTaggedLoggerMdcContext>()

  /**
   * Retrieves all the logging MDC tags that were added to the logger via `withSmartTags()` and
   * clears the thread local storage.
   *
   * Note: the thread local storage is only populated when an exception is thrown within a
   * `withSmartTags()` block.
   */
  fun popThreadLocalSmartTags() = threadLocalMdcContext
    .get()
    ?.tags
    ?.also { threadLocalMdcContext.remove() }
    ?: emptySet()

  internal fun clear() = threadLocalMdcContext.remove()

  internal fun addOrClearTags(th: Throwable, tags: Set<Tag>) {
    val existingContext = threadLocalMdcContext.get()

    if (existingContext == null || !existingContext.wasTriggeredBy(th)) {
      threadLocalMdcContext.set(ThreadLocalTaggedLoggerMdcContext(th, tags.toSet()))
    } else if (existingContext.wasTriggeredBy(th)) {
      threadLocalMdcContext.set(existingContext.copy(tags = existingContext.tags + tags.toSet()))
    } else {
      threadLocalMdcContext.remove()
    }
  }

  private data class ThreadLocalTaggedLoggerMdcContext(
    val triggeringThrowable: Throwable,
    val tags: Set<Tag>
  ) {
    fun wasTriggeredBy(throwable: Throwable): Boolean {
      return triggeringThrowable == throwable
    }
  }
}
