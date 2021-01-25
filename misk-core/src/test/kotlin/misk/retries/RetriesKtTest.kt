package misk.retries

import com.github.michaelbull.retry.policy.limitAttempts
import com.github.michaelbull.retry.policy.plus
import kotlinx.coroutines.runBlocking
import misk.testing.MiskTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class DifferentDummyException: Exception("I'm a different exception")

internal open class DummyException: Exception("Testing exception")

internal class DummySubException: DummyException()

@MiskTest
internal class RetriesTest {

  @Test
  fun doesNotRetryValidationException() {
    val policy = limitAttempts(2) + doNotRetry<DummyException>()

    var reads = 0
    var writes = 0

    assertThrows<Exception> {
      runBlocking {
        retryWithHooks(policy,
            beforeRetryHook = {
              reads++
            },
            op = {
              writes++
              throw DummyException()
            }
        )
      }
    }

    assertThat(reads).isEqualTo(0)
    assertThat(writes).isEqualTo(1)
  }

  @Test
  fun retriesException() {
    val policy = limitAttempts(2)

    var writes = 0
    var reads = 0

    assertThrows<Exception> {
      runBlocking {
        retryWithHooks(policy,
            beforeRetryHook = {
              reads++
            },
            op = {
              writes++
              throw DummyException()
            }
        )
      }
    }

    assertThat(reads).isEqualTo(1)
    assertThat(writes).isEqualTo(2)
  }


  @Test
  fun retriesSpecifiedException() {
    val policy = limitAttempts(2) + onlyRetry(DummyException::class)

    var writes = 0
    var reads = 0

    assertThrows<DummyException> {
      runBlocking {
        retryWithHooks(policy,
            beforeRetryHook = {
              reads++
            },
            op = {
              writes++
              throw DummyException()
            }
        )
      }
    }

    assertThat(reads).isEqualTo(1)
    assertThat(writes).isEqualTo(2)
  }

  @Test
  fun retriesSubclassOfSpecifiedException() {
    val policy = limitAttempts(2) +
        onlyRetry(DummyException::class, DifferentDummyException::class)

    var writes = 0
    var reads = 0

    assertThrows<DifferentDummyException> {
      runBlocking {
        retryWithHooks(policy,
            beforeRetryHook = {
              reads++
            },
            op = {
              writes++
              throw if (writes.rem(2) == 0) {
                DifferentDummyException()
              } else {
                DummyException()
              }
            }
        )
      }
    }

    assertThat(reads).isEqualTo(1)
    assertThat(writes).isEqualTo(2)
  }

  @Test
  fun retriesAllExceptions() {
    val policy = limitAttempts(2) + onlyRetry(DummyException::class)

    var writes = 0
    var reads = 0

    assertThrows<DummySubException> {
      runBlocking {
        retryWithHooks(policy,
            beforeRetryHook = {
              reads++
            },
            op = {
              writes++
              throw DummySubException()
            }
        )
      }
    }

    assertThat(reads).isEqualTo(1)
    assertThat(writes).isEqualTo(2)
  }

  @Test
  fun doesNotRetryUnspecifiedException() {
    val policy = limitAttempts(2) + onlyRetry(DummyException::class)

    var writes = 0
    var reads = 0

    assertThrows<Exception> {
      runBlocking {
        retryWithHooks(policy,
            beforeRetryHook = {
              reads++
            },
            op = {
              writes++
              throw Exception()
            }
        )
      }
    }

    assertThat(reads).isEqualTo(0)
    assertThat(writes).isEqualTo(1)
  }
}