package wisp.logging

import misk.annotation.ExperimentalMiskApi

@ExperimentalMiskApi
object SmartTagsThreadLocalHandler {
  private val threadLocalMdcContext = ThreadLocal<ThreadLocalSmartTagsMdcContext>()

  /**
   * Retrieves all the logging MDC tags that were added to the logger via `withSmartTags()` and
   * clears the thread local storage.
   *
   * Note: the thread local storage is only populated when an exception is thrown within a
   * `withSmartTags()` block.
   */
  fun popThreadLocalSmartTags() = peekThreadLocalSmartTags()
    .also { clearSmartTags() }

  /**
   * Retrieves all the logging MDC tags that were added to the logger via `withSmartTags()` but
   * leaves them in place. Useful for catching and logging exceptions within a nested withSmartTags
   * block.
   *
   * Note: the thread local storage is only populated when an exception is thrown within a
   * `withSmartTags()` block.
   */
  fun peekThreadLocalSmartTags() = threadLocalMdcContext
    .get()
    ?.tags
    ?: emptySet()

  private fun clearSmartTags() = threadLocalMdcContext.remove()

  private fun setOrAppendSmartTags(exception: Exception, tags: Set<Tag>) {
    val existingContext = threadLocalMdcContext.get()

    if (existingContext == null || !existingContext.wasTriggeredBy(exception)) {
      threadLocalMdcContext.set(ThreadLocalSmartTagsMdcContext(exception, tags.toSet()))
    } else if (existingContext.wasTriggeredBy(exception)) {
      threadLocalMdcContext.set(existingContext.copy(tags = existingContext.tags + tags.toSet()))
    }
  }

  internal fun <T> includeTagsOnExceptions(vararg tags: Tag, block: () -> T): T {
    try {
      return block().also {
        // Exiting this block gracefully: Lets do some cleanup to keep the ThreadLocal clear.
        // The scenario here is that when nested `withSmartTags` threw an exception and it was
        // caught and handled by this `withSmartTags`, it should clean up the unused and unneeded context.
        clearSmartTags()
      }
    } catch (e: Exception) {
      // Calls to `withSmartTags` can be nested - only set if there is not already a context set
      // This will be cleared upon logging of the exception within misk or if the thrown exception
      // is handled by a higher level `withSmartTags`
      setOrAppendSmartTags(e, tags.toSet())

      throw e
    }
  }

  private data class ThreadLocalSmartTagsMdcContext(
    val triggeringException: Exception,
    val tags: Set<Tag>
  ) {
    fun wasTriggeredBy(throwable: Throwable): Boolean {
      if (triggeringException == throwable)
        return true

      return if (throwable.cause != null) {
        wasTriggeredBy(throwable.cause!!)
      } else {
        false
      }
    }
  }
}
