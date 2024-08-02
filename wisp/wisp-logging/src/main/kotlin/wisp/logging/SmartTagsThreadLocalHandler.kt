package wisp.logging

object SmartTagsThreadLocalHandler {
  private val threadLocalMdcContext = ThreadLocal<ThreadLocalTaggedLoggerMdcContext>()

  fun clear() = threadLocalMdcContext.remove()

  fun popThreadLocalMdcContext() = threadLocalMdcContext
    .get()
    ?.tags
    ?.also { threadLocalMdcContext.remove() }
    ?: emptySet()

  fun addOrClearTags(th: Throwable, tags: Set<Tag>) {
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
