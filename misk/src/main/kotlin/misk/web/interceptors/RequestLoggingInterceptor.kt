package misk.web.interceptors

import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.Action
import misk.MiskCaller
import misk.scope.ActionScoped
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.interceptors.hooks.RequestResponseHook
import wisp.logging.getLogger

private val logger = getLogger<RequestLoggingInterceptor>()

/**
 * Logs request and response information for an action.
 * Timing information doesn't count time writing the response to the remote client.
 */
class RequestLoggingInterceptor internal constructor(
  private val caller: ActionScoped<MiskCaller?>,
  private val ticker: Ticker,
  private val bodyCapture: RequestResponseCapture,
  private val requestResponseHooks: List<RequestResponseHook>,
  private val requestLoggingTransformers: List<RequestLoggingTransformer>,
) : NetworkInterceptor {
  @Singleton
  class Factory @Inject internal constructor(
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
    private val ticker: Ticker,
    private val bodyCapture: RequestResponseCapture,
    private val requestResponseHookFactories: List<RequestResponseHook.Factory>,
    private val requestLoggingTransformers: List<RequestLoggingTransformer>,
  ) : NetworkInterceptor.Factory {
    override fun create(action: Action): NetworkInterceptor? {
      // Only bother with endpoints that have a hook annotation
      val requestResponseHooks = requestResponseHookFactories.mapNotNull { it.create(action) }
      if (requestResponseHooks.isEmpty()) return null

      return RequestLoggingInterceptor(
        caller = caller,
        ticker = ticker,
        bodyCapture = bodyCapture,
        requestResponseHooks = requestResponseHooks,
        requestLoggingTransformers = requestLoggingTransformers,
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
    }
    val elapsedToString = stopwatch.toString()
    val elapsed = stopwatch.elapsed()

    try {
      val requestResponse = bodyCapture.get()

      // Note that the order in which `RequestLoggingTransformer`s get applied is considered undefined
      // and cannot be reliably controlled by services.
      // In practice, they will be applied in the order that they happened to be bound.
      val transformedRequestResponseBody =
        requestLoggingTransformers.fold(requestResponse) { body, transformer ->
          transformer.tryTransform(body)
        }
      requestResponseHooks.forEach { hook ->
          hook.handle(
            caller = caller.get(),
            httpCall = chain.httpCall,
            requestResponse = transformedRequestResponseBody,
            elapsed = elapsed,
            elapsedToString = elapsedToString,
            error = error,
          )
      }
    } catch (e: Throwable) {
      logger.error(e) { "Unexpected error while logging request" }
    }

    if (error != null) {
      throw error
    }
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
data class ActionLoggingConfig @JvmOverloads constructor(
  val ratePerSecond: Long = 10,
  val errorRatePerSecond: Long = 0,
  val bodySampling: Double = 0.0,
  val errorBodySampling: Double = 0.0,
  val excludedEnvironments: List<String> = listOf(),
  val requestLoggingMode: RequestLoggingMode = RequestLoggingMode.ALL,
  val includeRequestHeaders: Boolean = false,
  val includeResponseHeaders: Boolean = false,
) {
  companion object {
    fun fromAnnotation(logRequestResponse: LogRequestResponse): ActionLoggingConfig = ActionLoggingConfig(
      ratePerSecond = logRequestResponse.ratePerSecond,
      errorRatePerSecond = logRequestResponse.errorRatePerSecond,
      bodySampling = logRequestResponse.bodySampling,
      errorBodySampling = logRequestResponse.errorBodySampling,
      excludedEnvironments = logRequestResponse.excludedEnvironments.toList(),
      requestLoggingMode = logRequestResponse.requestLoggingMode,
      includeRequestHeaders = logRequestResponse.includeRequestHeaders,
      includeResponseHeaders = logRequestResponse.includeResponseHeaders,
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
