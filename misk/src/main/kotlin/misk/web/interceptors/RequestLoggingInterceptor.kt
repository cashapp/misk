package misk.web.interceptors

import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import misk.Action
import misk.ApplicationInterceptor
import misk.Chain
import misk.MiskCaller
import misk.logging.getLogger
import misk.random.ThreadLocalRandom
import misk.scope.ActionScoped
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.full.findAnnotation

private val logger = getLogger<RequestLoggingInterceptor>()

/**
 * Logs request and response information for an action.
 * Timing information doesn't count time writing the response to the remote client.
 */
internal class RequestLoggingInterceptor internal constructor(
  private val action: Action,
  private val sampling: Double,
  private val includeBody: Boolean,
  private val caller: ActionScoped<MiskCaller?>,
  private val ticker: Ticker,
  private val random: ThreadLocalRandom
) : ApplicationInterceptor {

  @Singleton
  class Factory @Inject internal constructor(
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
    private val ticker: Ticker,
    private val random: ThreadLocalRandom
  ): ApplicationInterceptor.Factory {
    override fun create(action: Action): ApplicationInterceptor? {
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
        random
      )
    }
  }

  override fun intercept(chain: Chain): Any {
    val randomDouble = random.current().nextDouble()
    if (randomDouble >= sampling) {
      return chain.proceed(chain.args)
    }

    val principal = caller.get()?.principal ?: "unknown"
    val requestString = if (includeBody) chain.args.toString() else ""

    logger.info { "${action.name} principal=$principal request=$requestString" }

    val stopwatch = Stopwatch.createStarted(ticker)
    try {
      val result = chain.proceed(chain.args)
      val resultString = if (includeBody) result.toString() else ""
      logger.info { "${action.name} principal=$principal time=$stopwatch response=$resultString" }
      return result
    } catch (t: Throwable) {
      logger.info { "${action.name} principal=$principal time=$stopwatch failed" }
      throw t
    }
  }
}

/**
 * Annotation indicating that request and response information should be logged.
 *
 * sampling is used to sample the number of requests logged with 0.0 for none and 1.0 for all.
 * Valid values are in the range (0.0, 1.0].
 *
 * If includeBody is true both the action arguments and the response will be logged.
 *
 * If arguments and responses may include sensitive information, it is expected that the toString()
 * methods of these objects will redact it.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class LogRequestResponse(val sampling: Double, val includeBody: Boolean)
