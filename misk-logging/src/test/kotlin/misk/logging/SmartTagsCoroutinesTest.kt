package misk.logging

import java.util.concurrent.Executors
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Smart tags live in a thread local, and nothing copies them into coroutine context. Each dispatcher here is backed by
 * a single thread, so thread reuse and thread hops are deterministic rather than left to `Dispatchers.IO`.
 */
class SmartTagsCoroutinesTest {
  private val threadOne = singleThreadDispatcher("smart-tags-one")
  private val threadTwo = singleThreadDispatcher("smart-tags-two")

  @AfterEach
  fun closeDispatchers() {
    // Closing kills each dispatcher's only thread, taking any leftover thread local with it.
    threadOne.close()
    threadTwo.close()
  }

  @Test
  fun `leftover tags on a reused thread are not attached to an unrelated coroutine's error`() =
    runBlocking<Unit> {
      lateinit var swallowed: Throwable
      launch(threadOne) { swallowed = throwFromSmartTagsBlock("customer" to "A") }.join()

      launch(threadOne) {
          val unrelated = ProcessException("unrelated")

          assertThat(SmartTagsThreadLocalHandler.popThreadLocalSmartTags(unrelated)).isEmpty()
          // The miss also cleared the leftover from the thread
          assertThat(SmartTagsThreadLocalHandler.peekThreadLocalSmartTags(swallowed)).isEmpty()
        }
        .join()
    }

  @Test
  fun `tags are returned when the exception is logged on the thread that threw`() =
    runBlocking<Unit> {
      withContext(threadOne) {
        val thrown = throwFromSmartTagsBlock("customer" to "A")

        assertThat(SmartTagsThreadLocalHandler.popThreadLocalSmartTags(thrown)).containsExactly("customer" to "A")
      }
    }

  @Test
  fun `tags stay on the thread that threw - a thread hop before logging loses them`() =
    runBlocking<Unit> {
      // Smart tags are thread local, so log the exception on the thread that caught it, before switching dispatcher.
      withContext(threadOne) {
        val thrown = throwFromSmartTagsBlock("customer" to "A")

        withContext(threadTwo) { assertThat(SmartTagsThreadLocalHandler.popThreadLocalSmartTags(thrown)).isEmpty() }

        assertThat(SmartTagsThreadLocalHandler.peekThreadLocalSmartTags(thrown)).containsExactly("customer" to "A")
      }
    }

  /** Throws from inside a `withSmartTags` block and catches it outside, leaving the tags on the current thread. */
  private fun throwFromSmartTagsBlock(vararg tags: Tag): Throwable {
    try {
      withSmartTags(*tags) { throw ProcessException("boom") }
    } catch (th: ProcessException) {
      return th
    }
  }

  private fun singleThreadDispatcher(name: String): ExecutorCoroutineDispatcher =
    Executors.newSingleThreadExecutor { runnable -> Thread(runnable, name) }.asCoroutineDispatcher()

  class ProcessException(message: String) : Exception(message)
}
