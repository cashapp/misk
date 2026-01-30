package misk.micrometer

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import misk.Action
import misk.MiskCaller
import misk.scope.ActionScoped
import misk.time.timed
import misk.web.NetworkChain
import misk.web.NetworkInterceptor

/**
 * Records HTTP request metrics using Micrometer.
 *
 * This interceptor records:
 * - Request duration via a [Timer] named `http.server.requests`
 * - Tags include: action, method, status, caller, outcome, and exception (when present)
 *
 * The timer publishes percentile histograms that work well with Prometheus.
 */
internal class MicrometerWebActionMetricsInterceptor
internal constructor(
  private val actionName: String,
  private val requestTimer: Timer,
  private val caller: ActionScoped<MiskCaller?>,
  private val meterRegistry: MeterRegistry,
) : NetworkInterceptor {
  override fun intercept(chain: NetworkChain) {
    val sample = Timer.start()

    try {
      val (_, result) = timed { chain.proceed(chain.httpCall) }

      val callingPrincipal =
        when {
          caller.get()?.service != null -> caller.get()?.service!!
          caller.get()?.user != null -> "<user>"
          else -> "unknown"
        }

      val statusCode = chain.httpCall.statusCode
      val outcome =
        when (statusCode) {
          in 100..199 -> "INFORMATIONAL"
          in 200..299 -> "SUCCESS"
          in 300..399 -> "REDIRECTION"
          in 400..499 -> "CLIENT_ERROR"
          in 500..599 -> "SERVER_ERROR"
          else -> "UNKNOWN"
        }

      sample.stop(requestTimer)

      return result
    } catch (t: Throwable) {
      val callingPrincipal =
        when {
          caller.get()?.service != null -> caller.get()?.service!!
          caller.get()?.user != null -> "<user>"
          else -> "unknown"
        }

      val errorTimer =
        Timer.builder("http.server.requests")
          .tag("action", actionName)
          .tag("status", "500")
          .tag("caller", callingPrincipal)
          .tag("outcome", "SERVER_ERROR")
          .tag("exception", t.javaClass.simpleName)
          .register(meterRegistry)

      sample.stop(errorTimer)

      throw t
    }
  }

  @Singleton
  class Factory
  @Inject
  constructor(
    private val meterRegistry: MeterRegistry,
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
  ) : NetworkInterceptor.Factory {
    private val timers = ConcurrentHashMap<String, Timer>()

    override fun create(action: Action): NetworkInterceptor {
      val timer =
        timers.computeIfAbsent(action.name) {
          Timer.builder("http.server.requests")
            .description("HTTP server request duration")
            .publishPercentileHistogram()
            .register(meterRegistry)
        }
      return MicrometerWebActionMetricsInterceptor(action.name, timer, caller, meterRegistry)
    }
  }
}
