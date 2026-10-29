package misk.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class SmartTagsThreadLocalHandlerTest {
  @AfterEach
  fun clearThreadLocal() {
    SmartTagsThreadLocalHandler.clearSmartTags()
  }

  @Test
  fun `swallowed exception tags are not returned for an unrelated failure`() {
    val swallowed = throwFromSmartTagsBlock("customer" to "A") { ProcessException("swallowed") }
    val unrelated = ProcessException("unrelated")

    assertThat(SmartTagsThreadLocalHandler.popThreadLocalSmartTags(unrelated)).isEmpty()
    // The miss still cleared the thread local
    assertThat(SmartTagsThreadLocalHandler.peekThreadLocalSmartTags(swallowed)).isEmpty()
  }

  @Test
  fun `tags are returned for an exception wrapping the triggering exception`() {
    val triggering = throwFromSmartTagsBlock("customer" to "A") { ProcessException("inner") }
    val wrapper = RuntimeException("wrapper", triggering)

    assertThat(SmartTagsThreadLocalHandler.popThreadLocalSmartTags(wrapper)).containsExactly("customer" to "A")
  }

  @Test
  fun `nested rethrow wrapping the inner exception keeps both tag sets`() {
    val outer = catchThrowable {
      withSmartTags("outerTag" to "outerValue") {
        try {
          withSmartTags("innerTag" to "innerValue") { throw ProcessException("inner") }
        } catch (e: ProcessException) {
          throw OuterException(cause = e)
        }
      }
    }

    assertThat(SmartTagsThreadLocalHandler.popThreadLocalSmartTags(outer))
      .containsExactlyInAnyOrder("innerTag" to "innerValue", "outerTag" to "outerValue")
  }

  @Test
  fun `errors thrown in a smart tags block keep their tags`() {
    val error = catchThrowable { withSmartTags("customer" to "A") { TODO() } }

    assertThat(error).isInstanceOf(NotImplementedError::class.java)
    assertThat(SmartTagsThreadLocalHandler.popThreadLocalSmartTags(error)).containsExactly("customer" to "A")
  }

  @Test
  fun `peek leaves the tags in place and pop clears them`() {
    val th = throwFromSmartTagsBlock("customer" to "A") { ProcessException("boom") }

    assertThat(SmartTagsThreadLocalHandler.peekThreadLocalSmartTags(th)).containsExactly("customer" to "A")
    assertThat(SmartTagsThreadLocalHandler.peekThreadLocalSmartTags(th)).containsExactly("customer" to "A")
    assertThat(SmartTagsThreadLocalHandler.popThreadLocalSmartTags(th)).containsExactly("customer" to "A")
    assertThat(SmartTagsThreadLocalHandler.peekThreadLocalSmartTags(th)).isEmpty()
  }

  // Pins the deprecated behaviour until callers migrate. Delete along with the no-arg functions.
  @Suppress("DEPRECATION")
  @Test
  fun `deprecated no-arg peek and pop return tags regardless of the exception`() {
    throwFromSmartTagsBlock("customer" to "A") { ProcessException("swallowed") }

    assertThat(SmartTagsThreadLocalHandler.peekThreadLocalSmartTags()).containsExactly("customer" to "A")
    assertThat(SmartTagsThreadLocalHandler.popThreadLocalSmartTags()).containsExactly("customer" to "A")
    assertThat(SmartTagsThreadLocalHandler.peekThreadLocalSmartTags()).isEmpty()
  }

  @Test
  fun `cyclic cause chain terminates without a match`() {
    throwFromSmartTagsBlock("customer" to "A") { ProcessException("swallowed") }
    val a = ProcessException("a")
    val b = ProcessException("b")
    a.initCause(b)
    b.initCause(a)

    assertThat(SmartTagsThreadLocalHandler.popThreadLocalSmartTags(a)).isEmpty()
  }

  /** Throws from inside a `withSmartTags` block and swallows it outside, leaving the tags in the thread local. */
  private fun throwFromSmartTagsBlock(vararg tags: Tag, exception: () -> Throwable): Throwable = catchThrowable {
    withSmartTags(*tags) { throw exception() }
  }

  private fun catchThrowable(block: () -> Unit): Throwable {
    try {
      block()
    } catch (th: Throwable) {
      return th
    }
    throw AssertionError("Expected the block to throw")
  }

  class ProcessException(message: String) : Exception(message)

  class OuterException(cause: Throwable) : Exception("outer", cause)
}
