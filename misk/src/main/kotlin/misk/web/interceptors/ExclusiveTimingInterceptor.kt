package misk.web.interceptors

import com.google.common.base.Stopwatch
import com.google.inject.TypeLiteral
import io.prometheus.client.Histogram
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import kotlin.reflect.KClass
import misk.Action
import misk.MiskCaller
import misk.inject.KAbstractModule
import misk.metrics.v2.Metrics
import misk.scope.ActionScoped
import misk.web.NetworkChain
import misk.web.NetworkInterceptor

/**
 * Emits a metric measuring the amount of time spent in the request, excluding any time spent waiting on downstream.
 *
 * Use this by installing via `install(ExclusiveTimingInterceptor.Module())`, and then inject a
 * `ThreadLocal<DownstreamTime>` into places in your code where you want to exclude time.
 *
 * You can also use a custom interceptor factory if you want to customize the metric name and tags. Install it via
 * `install(ExclusiveTimingInterceptor.Module.of<MyExclusiveTimingInterceptorFactory>())` instead.
 */
class ExclusiveTimingInterceptor(
  private val excludedTime: ThreadLocal<ExcludedTime>,
  private val metric: Histogram,
  private val caller: ActionScoped<MiskCaller?>,
  private val actionName: String,
  private val getAdditionalTagValues: (ExcludedTime) -> List<String>,
) : NetworkInterceptor {
  override fun intercept(chain: NetworkChain) {
    val overallStopwatch = Stopwatch.createStarted()
    val downstreamTime = excludedTime.get()
    downstreamTime.clear()

    try {
      chain.proceed(chain.httpCall)
    } finally {
      val elapsed = overallStopwatch.stop().elapsed()
      val downstream = downstreamTime.value()
      val exclusiveMillis = (elapsed - downstream).toMillis().toDouble()

      val statusCode = chain.httpCall.statusCode.toString()
      val callingPrincipal = caller.get()?.service ?: caller.get()?.user?.let { "<user>" } ?: "unknown"

      val additionalTagValues = getAdditionalTagValues(downstreamTime).toTypedArray()

      metric.labels(actionName, callingPrincipal, statusCode, *additionalTagValues).observe(exclusiveMillis)
    }
  }

  @Singleton
  open class Factory
  @Inject
  constructor(
    private val excludedTime: ThreadLocal<ExcludedTime>,
    metrics: Metrics,
    private val caller: ActionScoped<MiskCaller?>,
  ) : NetworkInterceptor.Factory {
    open val metricName = "histo_http_request_exclusive_latency_ms"
    open val additionalTagNames = listOf<String>()

    open fun getAdditionalTagValues(excludedTime: ExcludedTime): List<String> = listOf()

    internal val requestDurationHistogram by lazy {
      metrics.histogram(
        name = metricName,
        help = "How much time was spent exclusively in Plasma and not waiting for downstream",
        labelNames = listOf("action", "caller", "status_code") + additionalTagNames,
      )
    }

    override fun create(action: Action): NetworkInterceptor {
      return ExclusiveTimingInterceptor(
        excludedTime,
        requestDurationHistogram,
        caller,
        action.name,
        ::getAdditionalTagValues,
      )
    }
  }

  class Module<T : Factory>(private var factoryClass: KClass<T>) : KAbstractModule() {
    override fun configure() {
      multibind<NetworkInterceptor.Factory>().to(factoryClass.java)
      bind(object : TypeLiteral<ThreadLocal<ExcludedTime>>() {}).toInstance(ThreadLocal.withInitial { ExcludedTime() })
    }

    companion object {
      operator fun invoke() = Module(Factory::class)

      inline fun <reified T : Factory> of(): Module<T> = Module(T::class)
    }
  }
}

class ExcludedTime @JvmOverloads constructor(private var time: Duration = Duration.ZERO) {
  /**
   * Any additional data that you want to be available to your [ExclusiveTimingInterceptor.Factory]. This can be useful
   * for creating additional metric tags.
   */
  var additionalData: MutableMap<String, Any> = mutableMapOf()

  /** Adds a length of time that should be excluded from the exclusive duration metric. */
  fun add(add: kotlin.time.Duration) {
    time = time.plusNanos(add.inWholeNanoseconds)
  }

  fun clear() {
    time = Duration.ZERO
  }

  fun value() = time
}
