package misk.metrics.backends.signalfx

import com.codahale.metrics.MetricRegistry
import com.google.common.util.concurrent.Service
import com.google.inject.Provides
import com.signalfx.codahale.reporter.SignalFxReporter
import com.signalfx.metrics.auth.StaticAuthToken
import misk.config.AppName
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.inject.to
import javax.inject.Singleton

class SignalFxBackendModule : KAbstractModule() {
  override fun configure() {
    binder().addMultibinderBinding<Service>()
        .to<SignalFxReporterService>()
  }

  @Provides
  @Singleton
  fun signalFxReporter(
      @AppName appName: String,
      config: SignalFxBackendConfig,
      metricRegistry: MetricRegistry
  ): SignalFxReporter {
    return SignalFxReporter.Builder(
        metricRegistry,
        StaticAuthToken(config.access_token),
        appName
    )
        .build()
  }
}
