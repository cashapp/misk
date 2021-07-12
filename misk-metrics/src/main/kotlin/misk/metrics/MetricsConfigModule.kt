package misk.metrics
import com.google.inject.name.Names
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import misk.inject.KAbstractModule
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

class MetricsConfigModule(
  private val metricsConfigs: List<MetricsConfig>
) : KAbstractModule() {
override fun configure() {
    metricsConfigs.forEach { config: MetricsConfig ->
      when (config.type) {
        MetricType.COUNTER -> {
          bind<Counter>()
            .annotatedWith(Names.named(config.name))
            .toProvider(object : Provider<Counter> {
              @Inject lateinit var metrics: Metrics
              override fun get(): Counter =
                metrics.counter(
                  name = config.name,
                  help = config.help,
                  labelNames = config.labelNames,
                )
            })
            .`in`(Singleton::class.java)
        }
        MetricType.GAUGE -> {
          bind<Gauge>()
            .annotatedWith(Names.named(config.name))
            .toProvider(object : Provider<Gauge> {
              @Inject lateinit var metrics: Metrics
              override fun get(): Gauge =
                metrics.gauge(
                  name = config.name,
                  help = config.help,
                  labelNames = config.labelNames,
                )
            })
            .`in`(Singleton::class.java)
        }
        MetricType.HISTOGRAM -> {
          bind<Histogram>()
            .annotatedWith(Names.named(config.name))
            .toProvider(object : Provider<Histogram> {
              @Inject lateinit var metrics: Metrics
              override fun get(): Histogram =
                metrics.histogram(
                  name = config.name,
                  help = config.help,
                  labelNames = config.labelNames,
                  quantiles = config.quantiles,
                )
            })
            .`in`(Singleton::class.java)
        }
      }
    }
  }
}

data class MetricsConfig(
  val name: String,
  val type: MetricType,
  val help: String = "",
  val labelNames: List<String> = listOf(),
  val quantiles: Map<Double, Double> = defaultQuantiles,
)

enum class MetricType {
  COUNTER,
  GAUGE,
  HISTOGRAM,
  ;
}
