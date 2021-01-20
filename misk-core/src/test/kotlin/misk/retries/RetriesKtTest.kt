package misk.retries

import com.github.michaelbull.retry.policy.limitAttempts
import com.github.michaelbull.retry.policy.plus
import kotlinx.coroutines.runBlocking
import misk.testing.MiskTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class DummyException: Exception("Testing exception")

@MiskTest
internal class RetriesTest {

  @Test
  fun doesNotRetryValidationException() {
    val policy = limitAttempts(2) + doNotRetry<DummyException>()

    var reads = 0
    var writes = 0

    assertThrows<Exception> {
      runBlocking {
        readWriteRetry(policy,
            reader = {
              reads++
            },
            writer = {
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
        readWriteRetry(policy,
            reader = {
              reads++
            },
            writer = {
              writes++
              throw DummyException()
            }
        )
      }
    }

    assertThat(reads).isEqualTo(1)
    assertThat(writes).isEqualTo(2)
  }
}