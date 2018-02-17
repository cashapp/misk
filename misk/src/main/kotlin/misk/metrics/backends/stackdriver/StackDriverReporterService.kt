package misk.metrics.backends.stackdriver

import com.google.common.util.concurrent.AbstractIdleService
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal class StackDriverReporterService @Inject internal constructor(
    private val config: StackDriverBackendConfig,
    private val reporter: StackDriverReporter
) : AbstractIdleService() {
  override fun startUp() = reporter.start(config.interval.toMillis(), TimeUnit.MILLISECONDS)
  override fun shutDown() = reporter.stop()
}
