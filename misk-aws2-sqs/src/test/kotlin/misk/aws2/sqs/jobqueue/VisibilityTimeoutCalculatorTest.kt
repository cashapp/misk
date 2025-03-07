package misk.aws2.sqs.jobqueue

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random

class VisibilityTimeoutCalculatorTest {
  private val calculator = VisibilityTimeoutCalculator()

  @Test
  fun `respects minimum visibility timeout`() {
    val queueVisibilityTimeout = 30
    val result = calculator.calculateVisibilityTimeout(
      currentReceiveCount = 1,
      queueVisibilityTimeout = queueVisibilityTimeout
    )
    
    assertThat(result).isGreaterThanOrEqualTo(queueVisibilityTimeout)
  }

  @Test
  fun `respects maximum visibility timeout`() {
    val result = calculator.calculateVisibilityTimeout(
      currentReceiveCount = 100, // Very high count
      queueVisibilityTimeout = 30
    )
    
    assertThat(result).isLessThanOrEqualTo(VisibilityTimeoutCalculator.MAX_JOB_DELAY.toInt())
  }

  @Test
  fun `increases timeout with receive count`() {
    val queueVisibilityTimeout = 1
    val firstRetry = calculator.calculateVisibilityTimeout(
      currentReceiveCount = 1,
      queueVisibilityTimeout = queueVisibilityTimeout
    )
    
    val secondRetry = calculator.calculateVisibilityTimeout(
      currentReceiveCount = 2,
      queueVisibilityTimeout = queueVisibilityTimeout
    )
    
    assertThat(secondRetry).isGreaterThan(firstRetry)
  }

  @Test
  fun `adds jitter to calculated timeout`() {
    val results = List(10) {
      calculator.calculateVisibilityTimeout(
        currentReceiveCount = 5,
        queueVisibilityTimeout = 1
      )
    }
    
    // With jitter, not all values should be the same
    assertThat(results.toSet().size).isGreaterThan(1)
  }

  @Test
  fun `caps retry count at maximum value`() {
    val maxCountResult = calculator.calculateVisibilityTimeout(
      currentReceiveCount = VisibilityTimeoutCalculator.MAX_RECEIVE_COUNT_FOR_BACKOFF,
      queueVisibilityTimeout = 30
    )
    
    val beyondMaxCountResult = calculator.calculateVisibilityTimeout(
      currentReceiveCount = VisibilityTimeoutCalculator.MAX_RECEIVE_COUNT_FOR_BACKOFF + 1,
      queueVisibilityTimeout = 30
    )
    
    // Should produce same range of values
    assertThat(beyondMaxCountResult).isEqualTo(maxCountResult)
  }
}
