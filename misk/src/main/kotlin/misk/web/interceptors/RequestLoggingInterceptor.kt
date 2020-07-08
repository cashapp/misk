package misk.web.interceptors

import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import misk.Action
import misk.MiskCaller
import misk.logging.getLogger
import misk.logging.info
import misk.random.ThreadLocalRandom
import misk.scope.ActionScoped
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.full.findAnnotation
import kotlin.text.StringBuilder

private val logger = getLogger<RequestLoggingInterceptor>()

/**
 * Logs request and response information for an action.
 * Timing information doesn't count time writing the response to the remote client.
 */
class RequestLoggingInterceptor internal constructor(
  private val action: Action,
  private val sampling: Double,
  private val includeBody: Boolean,
  private val caller: ActionScoped<MiskCaller?>,
  private val ticker: Ticker,
  private val random: ThreadLocalRandom,
  private val bodyCapture: RequestResponseCapture
) : NetworkInterceptor {
  @Singleton
  class Factory @Inject internal constructor(
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
    private val ticker: Ticker,
    private val random: ThreadLocalRandom,
    private val bodyCapture: RequestResponseCapture
  ) : NetworkInterceptor.Factory {
    override fun create(action: Action): NetworkInterceptor? {
      val logRequestResponse = action.function.findAnnotation<LogRequestResponse>() ?: return null
      require(0.0 < logRequestResponse.sampling && logRequestResponse.sampling <= 1.0) {
        "${action.name} @LogRequestResponse sampling must be in the range (0.0, 1.0]"
      }

      return RequestLoggingInterceptor(
        action,
        logRequestResponse.sampling,
        logRequestResponse.includeBody,
        caller,
        ticker,
        random,
        bodyCapture
      )
    }
  }

  override fun intercept(chain: NetworkChain) {
    val randomDouble = random.current().nextDouble()
    if (randomDouble >= sampling) {
      return chain.proceed(chain.httpCall)
    }

    val principal = caller.get()?.principal ?: "unknown"

    bodyCapture.clear()

    val stopwatch = Stopwatch.createStarted(ticker)

    try {
      val result = chain.proceed(chain.httpCall)
      stopwatch.stop()

      logRequestResponse(principal, stopwatch, chain.httpCall.statusCode)

      return result
    } catch (t: Throwable) {
      logRequestResponse(principal, stopwatch, null)
      throw t
    }
  }

  fun logRequestResponse(principal: String, stopwatch: Stopwatch, statusCode: Int?) {
    val builder = StringBuilder()
    builder.append("${action.name} principal=$principal time=$stopwatch")

    if (statusCode == null) {
      builder.append(" failed")
    } else {
      builder.append(" code=${statusCode}")
    }

    if (includeBody) {
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
    ) { builder.toString() }
  }
}
