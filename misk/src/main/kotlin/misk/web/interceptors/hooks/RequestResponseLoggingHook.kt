package misk.web.interceptors.hooks

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.Action
import misk.MiskCaller
import misk.annotation.ExperimentalMiskApi
import misk.random.ThreadLocalRandom
import misk.web.HttpCall
import misk.web.exceptions.ExceptionMapperResolver
import misk.web.exceptions.unwrap
import misk.web.interceptors.ActionLoggingConfig
import misk.web.interceptors.LogRateLimiter
import misk.web.interceptors.LogRateLimiter.LogBucketId
import misk.web.interceptors.LogRequestResponse
import misk.web.interceptors.RequestLoggingConfig
import misk.web.interceptors.RequestLoggingInterceptor
import misk.web.interceptors.RequestLoggingMode
import misk.web.interceptors.RequestResponseBody
import org.slf4j.event.Level
import wisp.deployment.Deployment
import wisp.logging.SmartTagsThreadLocalHandler
import wisp.logging.Tag
import wisp.logging.getLogger
import wisp.logging.log
import java.time.Duration
import kotlin.reflect.full.findAnnotation

/** For backwards compatibility, the interceptor logger is used since that is where the functionality used to live. */
private val logger = getLogger<RequestLoggingInterceptor>()

@OptIn(ExperimentalMiskApi::class)
internal class RequestResponseLoggingHook private constructor(
  private val action: Action,
  private val random: ThreadLocalRandom,
  private val logRateLimiter: LogRateLimiter,
  private val mapperResolver: ExceptionMapperResolver,
  private val requestResponseLoggedCapture: RequestResponseLoggedCapture,
  val config: ActionLoggingConfig,
) : RequestResponseHook {
  @Singleton
  class Factory @Inject constructor(
    private val random: ThreadLocalRandom,
    private val logRateLimiter: LogRateLimiter,
    private val deployment: Deployment,
    private val mapperResolver: ExceptionMapperResolver,
    private val requestResponseLoggedCapture: RequestResponseLoggedCapture,
    private val configs: Set<RequestLoggingConfig>,
  ) : RequestResponseHook.Factory {
    override fun create(action: Action): RequestResponseHook? {
      val annotation = action.function.findAnnotation<LogRequestResponse>()
        ?: return null

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

      return RequestResponseLoggingHook(
        action,
        random,
        logRateLimiter,
        mapperResolver,
        requestResponseLoggedCapture,
        config,
      )
    }
  }

  override fun handle(
    caller: MiskCaller?,
    httpCall: HttpCall,
    requestResponse: RequestResponseBody?,
    elapsed: Duration,
    elapsedToString: String,
    error: Throwable?
  ) {
    requestResponseLoggedCapture.reset()

    val statusCode = httpCall.statusCode
    val isError = statusCode > 299 || error != null
    if (!isError && config.requestLoggingMode == RequestLoggingMode.ERROR_ONLY) {
      return
    }

    val rateLimit = if (isError) config.errorRatePerSecond else config.ratePerSecond
    val loggingBucketId = LogBucketId(actionClass = action.name, isError = isError)
    if (!logRateLimiter.tryAcquire(loggingBucketId, rateLimit)) {
      return
    }

    val sampling = if (isError) config.errorBodySampling else config.bodySampling
    val randomDouble = random.current().nextDouble()
    val includeBody = randomDouble < sampling

    var additionalTags: Set<Tag> = setOf(
      "response_code" to statusCode,
      "response_time_millis" to elapsed.toMillis(),
   )

    val error = error?.unwrap()
    var level = Level.INFO

    if (error != null) {
      // Log with the exception and smart tags applied on error.
      level = mapperResolver.mapperFor(error)?.loggingLevel(error) ?: Level.ERROR
      additionalTags = additionalTags + SmartTagsThreadLocalHandler.peekThreadLocalSmartTags()
    }

    requestResponseLoggedCapture.onLogged()

    logger.log(
      level = level,
      th = error,
      tags = additionalTags.toTypedArray(),
    ) {
      buildDescription(
        caller = caller,
        httpCall = httpCall,
        elapsedToString = elapsedToString,
        requestResponse = requestResponse,
        error = error,
        includeBody = includeBody,
      )
    }
  }

  private fun buildDescription(
    caller: MiskCaller?,
    httpCall: HttpCall,
    elapsedToString: String,
    requestResponse: RequestResponseBody?,
    error: Throwable?,
    includeBody: Boolean,
  ): String = buildString {
    val principal = caller?.principal ?: "unknown"
    append("${action.name} principal=$principal time=${elapsedToString}")

    val statusCode = httpCall.statusCode
    if (error != null) {
      append(" failed")
    } else {
      append(" code=$statusCode")
    }

    if (includeBody) {
      requestResponse?.let {
        requestResponse.request?.let {
          append(" request=${requestResponse.request}")
        }
        requestResponse.requestHeaders?.let {
          append(" requestHeaders=${requestResponse.requestHeaders}")
        }
        requestResponse.response?.let {
          append(" response=${requestResponse.response}")
        }
        requestResponse.responseHeaders?.let {
          append(" responseHeaders=${requestResponse.responseHeaders}")
        }
      }
    }
  }
}

/**
 * Captures whether the RequestResponseLogger has already logged the response to avoid double logging.
 */
internal class RequestResponseLoggedCapture @Inject constructor() {
  companion object {
    private val capture = object : ThreadLocal<Boolean>() {
      override fun initialValue() = false
    }
  }

  fun isLogged(): Boolean = capture.get()

  fun onLogged() {
    capture.set(true)
  }

  fun reset() {
    return capture.set(false)
  }
}
