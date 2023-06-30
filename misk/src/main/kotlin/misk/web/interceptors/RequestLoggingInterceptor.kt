package misk.web.interceptors

import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import misk.Action
import misk.MiskCaller
import misk.random.ThreadLocalRandom
import misk.scope.ActionScoped
import misk.web.HttpCall
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.interceptors.LogRateLimiter.LogBucketId
import wisp.deployment.Deployment
import wisp.logging.getLogger
import wisp.logging.info
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
  private val bodyCapture: RequestResponseCapture,
  private val requestLoggingTransformers: List<RequestLoggingTransformer>,
) : NetworkInterceptor {
  @Singleton
  class Factory @Inject internal constructor(
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
    private val ticker: Ticker,
    private val random: ThreadLocalRandom,
    private val bodyCapture: RequestResponseCapture,
    private val logRateLimiter: LogRateLimiter,
    private val deployment: Deployment,
    private val configs: Set<RequestLoggingConfig>,
    private val requestLoggingTransformers: List<RequestLoggingTransformer>,
  ) : NetworkInterceptor.Factory {
    override fun create(action: Action): NetworkInterceptor? {
      // Only bother with endpoints that have the annotation
      val annotation = action.function.findAnnotation<LogRequestResponse>() ?: return null
      val config = ActionLoggingConfig.fromConfigMapOrAnnotation(action, configs, annotation)

      if (config.excludedEnvironments.contains(deployment.name)) {
        return null
      }
      require(config.ratePerSecond >= 0L) {
        "${action.name} @LogRequestResponse ratePerSecond must be >= 0"
      }
      require(config.errorRatePerSecond >= 0L) {
        "${action.name} @LogRequestResponse errorRatePerSecond must be >= 0"
      }
      require(config.bodySampling in 0.0..1.0) {
        "${action.name} @LogRequestResponse bodySampling must be in the range (0.0, 1.0]"
      }
      require(config.errorBodySampling in 0.0..1.0) {
        "${action.name} @LogRequestResponse errorBodySampling must be in the range (0.0, 1.0]"
      }

      return RequestLoggingInterceptor(
        action,
        caller,
        ticker,
        random,
        logRateLimiter,
        config.ratePerSecond,
        config.errorRatePerSecond,
        config.bodySampling,
        config.errorBodySampling,
        bodyCapture,
        requestLoggingTransformers,
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
      builder.append(" code=$statusCode")
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
      // Note that the order in which `RequestLoggingTransformer`s get applied is considered undefined
      // and cannot be reliably controlled by services.
      // In practice, they will be applied in the order that they happened to be bound.
      val requestResponseBody =
        requestLoggingTransformers.fold(bodyCapture.get()) { body, transformer ->
          transformer.tryTransform(body)
        }
      
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

/**
 * A set of per-action logging config overrides.
 */
data class RequestLoggingConfig(val actions: Map<String, ActionLoggingConfig>) {
}

/**
 * This class should have all the same config options as [LogRequestResponse]. See that class for details.
 */
data class ActionLoggingConfig(
  val ratePerSecond: Long = 10,
  val errorRatePerSecond: Long = 0,
  val bodySampling: Double = 0.0,
  val errorBodySampling: Double = 0.0,
  val excludedEnvironments: List<String> = listOf(),
) {
  companion object {
    fun fromAnnotation(logRequestResponse: LogRequestResponse): ActionLoggingConfig = ActionLoggingConfig(
      ratePerSecond = logRequestResponse.ratePerSecond,
      errorRatePerSecond = logRequestResponse.errorRatePerSecond,
      bodySampling = logRequestResponse.bodySampling,
      errorBodySampling = logRequestResponse.errorBodySampling,
      excludedEnvironments = logRequestResponse.excludedEnvironments.toList(),
    )

    fun fromConfigMapOrAnnotation(
      action: Action,
      configs: Set<RequestLoggingConfig>,
      annotation: LogRequestResponse
    ): ActionLoggingConfig {
      // Look for any configs that may have been provided from somewhere other than the annotation itself
      val endpointConfigs = configs.mapNotNull { it.actions[action.name] }.distinct()
      if (endpointConfigs.size > 1) {
        throw IllegalArgumentException("Found multiple conflicting configs for action [${action.name}]: $endpointConfigs")
      }

      // Fall back to using the annotation's config if no other configs were found
      return endpointConfigs.singleOrNull() ?: fromAnnotation(annotation)
    }
  }
}
