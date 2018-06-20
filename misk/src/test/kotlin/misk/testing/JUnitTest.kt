package misk.testing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class JUnitTest {
  @Test
  internal fun assertThrows() {
    val thrown = assertThrows<IllegalStateException> {
      throw IllegalStateException("boom")
    }
    assertThat(thrown).hasMessage("boom")
  }

  /**
   * Identical to the test above, but [AssertionError] is a special case because it's also what we
   * throw when things don't work.
   */
  @Test
  internal fun assertThrowsAssertionError() {
    val thrown = assertThrows<AssertionError> {
      throw AssertionError("boom")
    }
    assertThat(thrown).hasMessage("boom")
  }

  @Test
  internal fun assertThrowsFailsWithNoException() {
    var exceptionThrown: Throwable? = null
    try {
      assertThrows<IllegalStateException> {
        // Throw nothing!
      }
    } catch (e: Throwable) {
      exceptionThrown = e
    }

    assertThat(exceptionThrown).isInstanceOf(AssertionError::class.java)
    assertThat(exceptionThrown).hasMessage("expected IllegalStateException was not thrown")
  }

  @Test
  internal fun assertThrowsFailsWithWrongException() {
    var exceptionThrown: Throwable? = null
    try {
      assertThrows<IllegalStateException> {
        throw UnsupportedOperationException()
      }
    } catch (e: Throwable) {
      exceptionThrown = e
    }

    assertThat(exceptionThrown).isInstanceOf(AssertionError::class.java)
    assertThat(exceptionThrown)
        .hasMessage("expected IllegalStateException but was UnsupportedOperationException")
  }
}