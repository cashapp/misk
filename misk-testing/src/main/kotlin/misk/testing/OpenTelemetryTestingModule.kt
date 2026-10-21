package misk.testing

import com.google.inject.Provides
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.inject.KAbstractModule

internal class OpenTelemetryTestingModule : KAbstractModule() {
  override fun configure() {
    multibind<TestFixture>().to<OpenTelemetryFixture>()
  }

  @Provides fun openTelemetry(fixture: OpenTelemetryFixture): OpenTelemetry = fixture.sdk

  @Provides fun metricReader(fixture: OpenTelemetryFixture): InMemoryMetricReader = fixture.reader
}

@Singleton
internal class OpenTelemetryFixture @Inject constructor() : TestFixture {
  val reader: InMemoryMetricReader =
    InMemoryMetricReader.builder()
      .setAggregationTemporalitySelector(AggregationTemporalitySelector.deltaPreferred())
      .build()

  val sdk: OpenTelemetrySdk =
    OpenTelemetrySdk.builder().setMeterProvider(SdkMeterProvider.builder().registerMetricReader(reader).build()).build()

  override fun reset() {
    // Drain previous deltas while preserving the instruments held by reusable application singletons.
    reader.collectAllMetrics()
  }
}
