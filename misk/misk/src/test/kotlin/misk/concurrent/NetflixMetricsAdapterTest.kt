package misk.concurrent

import com.google.common.util.concurrent.AtomicDouble
import io.prometheus.client.CollectorRegistry
import javax.inject.Inject
import misk.ServiceManagerModule
import misk.inject.KAbstractModule
import misk.metrics.backends.prometheus.PrometheusConfig
import misk.metrics.backends.prometheus.PrometheusMetricsServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.ClockModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = false)
internal class NetflixMetricsAdapterTest {
  @MiskTestModule val module = TestModule()

  @Inject lateinit var netflixMetricsAdapter: NetflixMetricsAdapter
  @Inject lateinit var prometheusRegistry: CollectorRegistry

  @Test
  internal fun counter() {
    assertThat(countSample("test_gets", "status", "200")).isNull()

    val get200s = netflixMetricsAdapter.create("test_")
      .counter("gets", "status", "200")
    get200s.increment()
    get200s.increment()
    get200s.increment()

    assertThat(countSample("test_gets", "status", "200")).isEqualTo(3.0)
  }

  @Test
  internal fun histogram() {
    assertThat(p50Sample("test_call_times", "status", "200")).isNull()

    val callTimes200s = netflixMetricsAdapter.create("test_")
      .distribution("call_times", "status", "200")

    callTimes200s.addSample(100.0)
    callTimes200s.addSample(99.0)
    callTimes200s.addSample(101.0)

    assertThat(p50Sample("test_call_times", "status", "200")).isIn(99.0, 100.0, 101.0)
  }

  @Test
  internal fun `multiple counters`() {
    val netflixRegistry = netflixMetricsAdapter.create("test_")
    val get200s = netflixRegistry.counter("gets", "status", "200")
    val get503s = netflixRegistry.counter("gets", "status", "503")
    val post200s = netflixRegistry.counter("posts", "status", "200")
    get200s.increment()
    get503s.increment()
    post200s.increment()
    post200s.increment()
    get200s.increment()
    get200s.increment()

    assertThat(countSample("test_gets", "status", "200")).isEqualTo(3.0)
    assertThat(countSample("test_gets", "status", "503")).isEqualTo(1.0)
    assertThat(countSample("test_posts", "status", "200")).isEqualTo(2.0)
  }

  @Test
  internal fun gauge() {
    val runningCount = AtomicDouble(0.0)
    assertThat(countSample("thread_count", "state", "running")).isNull()
    netflixMetricsAdapter.create("test_").gauge(
      "thread_count",
      { runningCount.get() },
      "state",
      "running"
    )

    runningCount.set(20.0)
    netflixMetricsAdapter.updateGauges()
    assertThat(countSample("test_thread_count", "state", "running")).isEqualTo(20.0)

    runningCount.set(23.0)
    netflixMetricsAdapter.updateGauges()
    assertThat(countSample("test_thread_count", "state", "running")).isIn(20.0, 23.0)
  }

  @Test
  internal fun `multiple histograms`() {
    val netflixRegistry = netflixMetricsAdapter.create("test_")
    val callTimes200s = netflixRegistry.distribution("call_times", "status", "200")
    val callTimes503s = netflixRegistry.distribution("call_times", "status", "503")
    val callSizes200s = netflixRegistry.distribution("call_sizes", "status", "200")

    callTimes200s.addSample(100.0)
    callTimes503s.addSample(30.0)
    callSizes200s.addSample(2048.0)

    assertThat(p50Sample("test_call_times", "status", "200")).isEqualTo(100.0)
    assertThat(p50Sample("test_call_times", "status", "503")).isEqualTo(30.0)
    assertThat(p50Sample("test_call_sizes", "status", "200")).isEqualTo(2048.0)
  }

  @Test
  internal fun `multiple gauges`() {
    val sleepingThreadCount = AtomicDouble(0.0)
    val runningCount = AtomicDouble(0.0)
    val sendingCount = AtomicDouble(0.0)

    val netflixRegistry = netflixMetricsAdapter.create("test_")
    netflixRegistry.gauge(
      "thread_count",
      { sleepingThreadCount.get() },
      "state",
      "sleeping"
    )
    netflixRegistry.gauge(
      "thread_count",
      { runningCount.get() },
      "state",
      "running"
    )
    netflixRegistry.gauge(
      "socket_count",
      { sendingCount.get() },
      "state",
      "sending"
    )

    sleepingThreadCount.set(10.0)
    runningCount.set(20.0)
    sendingCount.set(456.0)
    netflixMetricsAdapter.updateGauges()

    assertThat(countSample("test_thread_count", "state", "sleeping")).isEqualTo(10.0)
    assertThat(countSample("test_thread_count", "state", "running")).isEqualTo(20.0)
    assertThat(countSample("test_socket_count", "state", "sending")).isEqualTo(456.0)
  }

  private fun countSample(name: String, labelName: String, labelValue: String): Double? =
    prometheusRegistry.getSampleValue(
      name,
      arrayOf(labelName),
      arrayOf(labelValue)
    )

  private fun p50Sample(name: String, labelName: String, labelValue: String): Double? =
    prometheusRegistry.getSampleValue(
      name,
      arrayOf(labelName, "quantile"),
      arrayOf(labelValue, "0.5")
    )

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(ClockModule())
      install(ExecutorsModule())
      install(PrometheusMetricsServiceModule(PrometheusConfig(http_port = 0)))
      install(NetflixMetricsAdapter.MODULE)
      install(ServiceManagerModule())
    }
  }
}
