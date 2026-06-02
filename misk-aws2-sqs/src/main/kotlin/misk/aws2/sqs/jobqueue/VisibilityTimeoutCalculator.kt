package misk.aws2.sqs.jobqueue

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.math.BigInteger
import kotlin.random.Random

@Singleton
class VisibilityTimeoutCalculator @Inject constructor() {
  /**
   * Calculates exponential backoff visibility timeout based on the receive count. At minimum, it should be equal to
   * provided queue visibility timeout. At maximum, it should be 12 hours, which is the highest visibility timeout
   * allowed by SQS.
   *
   * Calculated visibility timeout adds some jitter.
   */
  fun calculateVisibilityTimeout(currentReceiveCount: Int, queueVisibilityTimeout: Int): Int {
    val consecutiveRetryCount = currentReceiveCount.coerceAtMost(MAX_RECEIVE_COUNT_FOR_BACKOFF)
    val backoff = BigInteger.TWO.pow(consecutiveRetryCount).toLong()
    val backoffWithJitter =
      MAX_JOB_DELAY.coerceAtMost((backoff / 2 + Random.nextLong(0, backoff / 2)))
        .coerceAtLeast(queueVisibilityTimeout.toLong())

    return backoffWithJitter.toInt()
  }

  companion object {
    /** We are limited with 12hrs after which SQS would throw an exception */
    const val MAX_JOB_DELAY = 12 * 60 * 60L

    /**
     * We calculate new visibility timeout for backoff based on the receive count. Going beyond 17 will exceed
     * MAX_JOB_DELAY anyway. 2^17/2=65536 which is more than 43200 (12 hours)
     */
    const val MAX_RECEIVE_COUNT_FOR_BACKOFF = 17
  }
}
