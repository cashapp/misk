package misk.metrics

import io.prometheus.client.Counter
import io.prometheus.client.CounterMetricFamily
import io.prometheus.client.Gauge
import io.prometheus.client.GaugeMetricFamily
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.config.ConfigSource
import wisp.config.WispConfig
import wisp.config.addWispConfigSources
import javax.inject.Inject
import javax.inject.Named

@MiskTest
internal class MetricsConfigTest {
  @Suppress("unused")
  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      install(FakeMetricsModule())

      val builder = WispConfig.builder()
      builder.addWispConfigSources(listOf(ConfigSource("classpath:/metrics.yaml")))

      val myConfig = builder.build().loadConfigOrThrow<MetricsConfigYaml>()
      install(MetricsConfigModule(myConfig.metrics))
    }
  }

  data class MetricsConfigYaml(val metrics: List<MetricsConfig>)


  @Inject @field:Named("test_counter") private lateinit var counter: Counter
  @Inject @field:Named("test_gauge") private lateinit var gauge: Gauge
  @Inject @field:Named("test_histogram") private lateinit var histogram: Histogram

  @Test
  fun `config from yaml gets converted into counter bindings`() {
    assertThat(counter).isNotNull
    val metadata = counter.describe()[0]!! as CounterMetricFamily
    assertThat(metadata.name).isEqualTo("test_counter")
    assertThat(metadata.help).isEqualTo("Test counter")

    val sample = metadata.addMetric(listOf("test"), 1.0).samples[0]!!
    assertThat(sample.labelNames).containsExactly("label_c")
  }

  @Test
  fun `config from yaml gets converted into gauge bindings`() {
    assertThat(gauge).isNotNull
    val metadata = gauge.describe()[0]!! as GaugeMetricFamily
    assertThat(metadata.name).isEqualTo("test_gauge")
    assertThat(metadata.help).isEqualTo("Test gauge")

    val sample =metadata.addMetric(listOf("test1", "test2"), 1.0).samples[0]!!
    assertThat(sample.labelNames).containsExactlyInAnyOrder("label_d", "label_e")
  }

  @Test
  fun `config from yaml gets converted into histogram bindings`() {
    assertThat(histogram).isNotNull
    // FIXME: Find a better was to test the histogram. It can't be described like above.
  }
}
