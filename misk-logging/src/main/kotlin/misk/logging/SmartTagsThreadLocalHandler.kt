package misk.logging

import java.util.Collections
import java.util.IdentityHashMap

object SmartTagsThreadLocalHandler {
  private val threadLocalMdcContext = ThreadLocal<ThreadLocalSmartTagsMdcContext>()

  /** Guards against pathologically deep cause chains when matching a throwable to the stored tags. */
  private const val MAX_CAUSE_CHAIN_DEPTH = 100

  /**
   * Retrieves the logging MDC tags that were added via `withSmartTags()` when [th] (or one of its causes) escaped those
   * blocks. Returns an empty set if the stored tags belong to a different exception.
   *
   * The thread local storage is always cleared, whether or not the tags matched [th], so tags left behind by an
   * exception that was swallowed can't be picked up later.
   *
   * Pass the outermost throwable being logged, not an unwrapped cause.
   */
  fun popThreadLocalSmartTags(th: Throwable): Set<Tag> = peekThreadLocalSmartTags(th).also { clearSmartTags() }

  /**
   * Retrieves the logging MDC tags that were added via `withSmartTags()` when [th] (or one of its causes) escaped those
   * blocks, but leaves them in place. Returns an empty set if the stored tags belong to a different exception. Useful
   * for catching and logging exceptions within a nested withSmartTags block.
   *
   * Pass the outermost throwable being logged, not an unwrapped cause.
   */
  fun peekThreadLocalSmartTags(th: Throwable): Set<Tag> {
    val context = threadLocalMdcContext.get() ?: return emptySet()
    return if (context.wasTriggeredBy(th)) context.tags else emptySet()
  }

  /**
   * Retrieves all the logging MDC tags that were added to the logger via `withSmartTags()` and clears the thread local
   * storage.
   *
   * Note: the thread local storage is only populated when an exception is thrown within a `withSmartTags()` block. The
   * tags are returned regardless of which exception stored them, so they may belong to an earlier exception that was
   * swallowed rather than the one being logged.
   */
  @Deprecated(
    message =
      "Returns tags without checking they belong to the exception being logged. " +
        "Pass the throwable being logged: popThreadLocalSmartTags(th).",
    level = DeprecationLevel.WARNING,
  )
  @Suppress("DEPRECATION")
  fun popThreadLocalSmartTags(): Set<Tag> = peekThreadLocalSmartTags().also { clearSmartTags() }

  /**
   * Retrieves all the logging MDC tags that were added to the logger via `withSmartTags()` but leaves them in place.
   * Useful for catching and logging exceptions within a nested withSmartTags block.
   *
   * Note: the thread local storage is only populated when an exception is thrown within a `withSmartTags()` block. The
   * tags are returned regardless of which exception stored them, so they may belong to an earlier exception that was
   * swallowed rather than the one being logged.
   */
  @Deprecated(
    message =
      "Returns tags without checking they belong to the exception being logged. " +
        "Pass the throwable being logged: peekThreadLocalSmartTags(th).",
    level = DeprecationLevel.WARNING,
  )
  fun peekThreadLocalSmartTags(): Set<Tag> = threadLocalMdcContext.get()?.tags ?: emptySet()

  /** Visible for tests, so they can reset the thread local without relying on the deprecated no-arg pop. */
  internal fun clearSmartTags() = threadLocalMdcContext.remove()

  private fun setOrAppendSmartTags(throwable: Throwable, tags: Set<Tag>) {
    val existingContext = threadLocalMdcContext.get()

    if (existingContext == null || !existingContext.wasTriggeredBy(throwable)) {
      threadLocalMdcContext.set(ThreadLocalSmartTagsMdcContext(throwable, tags.toSet()))
    } else {
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
    } catch (e: Throwable) {
      // Calls to `withSmartTags` can be nested - only set if there is not already a context set
      // This will be cleared upon logging of the exception within misk or if the thrown exception
      // is handled by a higher level `withSmartTags`
      setOrAppendSmartTags(e, tags.toSet())

      throw e
    }
  }

  private data class ThreadLocalSmartTagsMdcContext(val triggeringException: Throwable, val tags: Set<Tag>) {
    /** True if [throwable] is the triggering exception or wraps it somewhere in its cause chain. */
    fun wasTriggeredBy(throwable: Throwable): Boolean {
      val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
      var current: Throwable? = throwable

      while (current != null && visited.size < MAX_CAUSE_CHAIN_DEPTH && visited.add(current)) {
        if (current === triggeringException) return true
        current = current.cause
      }

      return false
    }
  }
}
