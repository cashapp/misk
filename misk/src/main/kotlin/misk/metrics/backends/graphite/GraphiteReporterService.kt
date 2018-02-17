package misk.metrics.backends.graphite

import com.codahale.metrics.graphite.GraphiteReporter
import com.google.common.util.concurrent.AbstractIdleService
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal class GraphiteReporterService @Inject internal constructor(
    private val config: GraphiteBackendConfig,
    private val reporter: GraphiteReporter
) : AbstractIdleService() {
  override fun startUp() = reporter.start(config.interval.toMillis(), TimeUnit.MILLISECONDS)
  override fun shutDown() = reporter.stop()
}
