package misk.metrics.backends.signalfx

import com.google.common.util.concurrent.AbstractIdleService
import com.signalfx.codahale.reporter.SignalFxReporter
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal class SignalFxReporterService @Inject internal constructor(
    private val config: SignalFxBackendConfig,
    private val reporter: SignalFxReporter
) : AbstractIdleService() {
  override fun startUp() = reporter.start(config.interval.toMillis(), TimeUnit.MILLISECONDS)
  override fun shutDown() = reporter.stop()
}
