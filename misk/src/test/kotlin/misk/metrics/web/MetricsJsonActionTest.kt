package misk.metrics.web

import com.google.common.truth.Truth.assertThat
import misk.metrics.Metrics
import misk.metrics.MetricsModule
import misk.testing.ActionTest
import misk.testing.ActionTestModule
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ActionTest
internal class MetricsJsonActionTest {
    @ActionTestModule
    val module = MetricsModule()

    @Inject internal lateinit var metrics: Metrics
    @Inject internal lateinit var metricsAction: MetricsJsonAction

    @Test
    fun exposeMetrics() {
        metrics.counter("myfav").inc(32)
        metrics.counter("banana").inc(17)
        metrics.counter("joist").inc(5)
        metrics.timer("slow").update(392, TimeUnit.MILLISECONDS)
        metrics.timer("slow").update(256, TimeUnit.MILLISECONDS)
        metrics.histogram("mork").update(24)
        metrics.histogram("mork").update(17)
        metrics.histogram("mork").update(27)
        metrics.gauge("g1", { 42 })
        metrics.gauge("g2", { 3.14 })
        metrics.gauge("g3", { "howdy pardner" })

        val json = metricsAction.getMetrics()
        assertThat(json.counters.keys).containsExactly("myfav", "banana", "joist")
        assertThat(json.timers.keys).containsExactly("slow")
        assertThat(json.histograms.keys).containsExactly("mork")
        assertThat(json.gauges.keys).containsExactly("g1", "g2", "g3")
    }
}
