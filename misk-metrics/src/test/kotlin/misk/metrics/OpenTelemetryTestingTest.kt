package misk.metrics

import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import java.util.concurrent.atomic.AtomicLong
import misk.inject.getInstance
import misk.metrics.v2.FakeMetricsModule as FakeMetricsModuleV2
import misk.testing.TestFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class OpenTelemetryTestingTest {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `fake metrics collect and drain counter deltas between tests`(legacyApi: Boolean) {
    val module = if (legacyApi) FakeMetricsModule() else FakeMetricsModuleV2()
    val injector = Guice.createInjector(module)
    val telemetry = injector.getInstance<OpenTelemetry>()
    val reader = injector.getInstance<InMemoryMetricReader>()
    val meter = telemetry.getMeter("test")
    val counter = meter.counterBuilder("requests").build()
    val total = AtomicLong(3)
    meter.counterBuilder("observed").buildWithCallback { it.record(total.get()) }

    counter.add(7)
    val fixtures = injector.getInstance(Key.get(object : TypeLiteral<Set<TestFixture>>() {}))
    fixtures.forEach { it.reset() }

    counter.add(2)
    total.addAndGet(4)
    val collected = reader.collectAllMetrics().associateBy { it.name }
    assertThat(collected.getValue("requests").longSumData.points.single().value).isEqualTo(2L)
    assertThat(collected.getValue("observed").longSumData.points.single().value).isEqualTo(4L)
  }

  @Test
  fun `reusing a module gives each injector its own SDK and reader`() {
    val module = FakeMetricsModuleV2()
    val first = Guice.createInjector(module)
    val second = Guice.createInjector(module)
    val firstTelemetry = first.getInstance<OpenTelemetry>()
    val secondTelemetry = second.getInstance<OpenTelemetry>()
    assertThat(firstTelemetry).isNotSameAs(secondTelemetry)

    firstTelemetry.getMeter("test").counterBuilder("requests").build().add(1)
    assertThat(first.getInstance<InMemoryMetricReader>().collectAllMetrics()).hasSize(1)
    assertThat(second.getInstance<InMemoryMetricReader>().collectAllMetrics()).isEmpty()
  }
}
