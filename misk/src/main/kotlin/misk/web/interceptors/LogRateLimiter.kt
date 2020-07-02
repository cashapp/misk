package misk.web.interceptors

import misk.ratelimit.RateLimiter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

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

  fun tryAcquire(bucketId: LogBucketId): Boolean {
    val logRequestResponse = bucketId.actionClass.java.getAnnotation(LogRequestResponse::class.java)
    val bucketQps =
      if (bucketId.isError) logRequestResponse.errorRateLimiting else logRequestResponse.rateLimiting
    if (bucketQps == 0L) {
      // If bucketQps is zero, it was specified by the service owner in the [LogRequestResponse]
      // annotation meaning rate limiting on logs should be off
      return true
    }
    val rateLimiter = rateLimiters.getOrPut(bucketId) { rateLimiterFactory.create(bucketQps) }
    return rateLimiter.tryAcquire(1, 0L, TimeUnit.MILLISECONDS)
  }

  data class LogBucketId(
    /** Calling service, this comes from misk headers. */
    val caller: String,
    /** ActionClass from which we can grab the [LogRequestResponse] **/
    val actionClass: KClass<*>,
    /** If the response code is error, we look up the errorRateLimiter **/
    val isError: Boolean
  ) : Comparable<LogBucketId> {
    override fun toString() = "$caller/${actionClass.simpleName}/${isError}"

    override fun compareTo(other: LogBucketId) = toString().compareTo(other.toString())
  }
}