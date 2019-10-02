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

private val logger = getLogger<RequestLoggingInterceptor>()

/**
 * Logs request and response information for an action.
 * Timing information doesn't count time writing the response to the remote client.
 */
class RequestLoggingInterceptor internal constructor(
  private val action: Action,
  private val sampling: Double,
  private val caller: ActionScoped<MiskCaller?>,
  private val ticker: Ticker,
  private val random: ThreadLocalRandom
) : NetworkInterceptor {
  @Singleton
  class Factory @Inject internal constructor(
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
    private val ticker: Ticker,
    private val random: ThreadLocalRandom
  ) : NetworkInterceptor.Factory {
    override fun create(action: Action): NetworkInterceptor? {
      val logRequestResponse = action.function.findAnnotation<LogRequestResponse>() ?: return null
      require(0.0 < logRequestResponse.sampling && logRequestResponse.sampling <= 1.0) {
        "${action.name} @LogRequestResponse sampling must be in the range (0.0, 1.0]"
      }

      return RequestLoggingInterceptor(
        action,
        logRequestResponse.sampling,
        caller,
        ticker,
        random
      )
    }
  }

  override fun intercept(chain: NetworkChain) {
    val randomDouble = random.current().nextDouble()
    if (randomDouble >= sampling) {
      return chain.proceed(chain.httpCall)
    }

    val principal = caller.get()?.principal ?: "unknown"

    logger.info { "${action.name} principal=$principal" }

    val stopwatch = Stopwatch.createStarted(ticker)
    try {
      val result = chain.proceed(chain.httpCall)
      stopwatch.stop()
      logger.info(
          "response_code" to chain.httpCall.statusCode,
          "response_time_millis" to stopwatch.elapsed(TimeUnit.MILLISECONDS)
      ) { "${action.name} principal=$principal time=$stopwatch code=${chain.httpCall.statusCode}" }
      return result
    } catch (t: Throwable) {
      logger.info { "${action.name} principal=$principal time=$stopwatch failed" }
      throw t
    }
  }
}
