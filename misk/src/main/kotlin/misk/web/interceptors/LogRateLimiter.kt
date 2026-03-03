package misk.web.interceptors

import misk.sampling.RateLimiter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds rate limiters for logging success and error responses. There is a rate limiter for every
 * action and service that calls it, for both success and error. The rate limiters are created
 * according to the value set in [LogRequestResponse] annotation.
 */
@Singleton
class LogRateLimiter @Inject constructor(
  private val rateLimiterFactory: RateLimiter.Factory
) {
  private val rateLimiters = ConcurrentHashMap<LogBucketId, RateLimiter>()

  fun tryAcquire(bucketId: LogBucketId, ratePerSecond: Long): Boolean {
    if (ratePerSecond == 0L) {
      return true
    }

    val rateLimiter = rateLimiters.getOrPut(bucketId) { rateLimiterFactory.create(ratePerSecond) }
    return rateLimiter.tryAcquire(1, 0L, TimeUnit.SECONDS)
  }

  data class LogBucketId(
    /** ActionClass from which we can grab the [LogRequestResponse] **/
    val actionClass: String,
    /** If the response code is error, we look up the errorRateLimiter **/
    val isError: Boolean
  ) : Comparable<LogBucketId> {
    override fun toString() = "${actionClass}/${isError}"

    override fun compareTo(other: LogBucketId) = toString().compareTo(other.toString())
  }
}
