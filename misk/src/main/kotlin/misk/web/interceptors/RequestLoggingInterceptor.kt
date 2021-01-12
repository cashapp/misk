package misk.web.interceptors

import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import misk.Action
import misk.MiskCaller
import misk.logging.getLogger
import misk.logging.info
import misk.random.ThreadLocalRandom
import misk.scope.ActionScoped
import misk.web.HttpCall
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.interceptors.LogRateLimiter.LogBucketId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.full.findAnnotation

private val logger = getLogger<RequestLoggingInterceptor>()

/**
 * Logs request and response information for an action.
 * Timing information doesn't count time writing the response to the remote client.
 */
class RequestLoggingInterceptor internal constructor(
  private val action: Action,
  private val caller: ActionScoped<MiskCaller?>,
  private val ticker: Ticker,
  private val random: ThreadLocalRandom,
  private val logRateLimiter: LogRateLimiter,
  private val ratePerSecond: Long,
  private val errorRatePerSecond: Long,
  private val bodySampling: Double,
  private val errorBodySampling: Double,
  private val bodyCapture: RequestResponseCapture
) : NetworkInterceptor {
  @Singleton
  class Factory @Inject internal constructor(
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
    private val ticker: Ticker,
    private val random: ThreadLocalRandom,
    private val bodyCapture: RequestResponseCapture,
    private val logRateLimiter: LogRateLimiter
  ) : NetworkInterceptor.Factory {
    override fun create(action: Action): NetworkInterceptor? {
      val logRequestResponse = action.function.findAnnotation<LogRequestResponse>() ?: return null
      require(logRequestResponse.ratePerSecond >= 0L) {
        "${action.name} @LogRequestResponse ratePerSecond must be >= 0"
      }
      require(logRequestResponse.errorRatePerSecond >= 0L) {
        "${action.name} @LogRequestResponse errorRatePerSecond must be >= 0"
      }
      require(logRequestResponse.bodySampling in 0.0..1.0) {
        "${action.name} @LogRequestResponse bodySampling must be in the range (0.0, 1.0]"
      }
      require(logRequestResponse.errorBodySampling in 0.0..1.0) {
        "${action.name} @LogRequestResponse errorBodySampling must be in the range (0.0, 1.0]"
      }

      return RequestLoggingInterceptor(
        action,
        caller,
        ticker,
        random,
        logRateLimiter,
        logRequestResponse.ratePerSecond,
        logRequestResponse.errorRatePerSecond,
        logRequestResponse.bodySampling,
        logRequestResponse.errorBodySampling,
        bodyCapture
      )
    }
  }

  override fun intercept(chain: NetworkChain) {
    bodyCapture.clear()

    val stopwatch = Stopwatch.createStarted(ticker)

    var error: Throwable? = null
    try {
      chain.proceed(chain.httpCall)
    } catch (e: Throwable) {
      error = e
    } finally {
      stopwatch.stop()
    }

    try {
      maybeLog(chain.httpCall, stopwatch, error)
    } catch (e: Throwable) {
      logger.error(e) { "Unexpected error while logging request" }
    }

    if (error != null) {
      throw error
    }
  }

  fun maybeLog(httpCall: HttpCall, stopwatch: Stopwatch, error: Throwable?) {
    val principal = caller.get()?.principal ?: "unknown"

    val builder = StringBuilder()
    builder.append("${action.name} principal=$principal time=$stopwatch")

    val statusCode = httpCall.statusCode
    if (error != null) {
      builder.append(" failed")
    } else {
      builder.append(" code=${statusCode}")
    }

    val isError = statusCode > 299 || error != null

    val rateLimit = if (isError) errorRatePerSecond else ratePerSecond
    val loggingBucketId = LogBucketId(actionClass = action.name, isError = isError)
    if (!logRateLimiter.tryAcquire(loggingBucketId, rateLimit)) {
      return
    }

    val sampling = if (isError) errorBodySampling else bodySampling
    val randomDouble = random.current().nextDouble()
    if (randomDouble < sampling) {
      val requestResponseBody = bodyCapture.get()
      requestResponseBody?.let {
        requestResponseBody.request?.let {
          builder.append(" request=${requestResponseBody.request}")
        }
        requestResponseBody.response?.let {
          builder.append(" response=${requestResponseBody.response}")
        }
      }
    }


    logger.info(
      "response_code" to statusCode,
      "response_time_millis" to stopwatch.elapsed(TimeUnit.MILLISECONDS)
    )
    { builder.toString() }
  }
}
