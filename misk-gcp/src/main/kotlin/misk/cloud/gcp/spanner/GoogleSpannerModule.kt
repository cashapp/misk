package misk.cloud.gcp.spanner

import com.google.auth.Credentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.NoCredentials
import com.google.cloud.http.HttpTransportOptions
import com.google.cloud.spanner.Spanner
import com.google.cloud.spanner.SpannerOptions
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import wisp.deployment.Deployment
import javax.inject.Inject
import javax.inject.Provider

/**
 * [GoogleSpannerModule] provides a Google Spanner client for your app.
 *
 * For testing, install the emulator
 */
class GoogleSpannerModule(
  private val spannerConfig: SpannerConfig,
) : KAbstractModule() {
  override fun configure() {
    bind<SpannerConfig>().toInstance(spannerConfig)
    install(ServiceModule<GoogleSpannerEmulator>())
  }

  @Provides
  @Singleton
  fun provideCloudSpanner(config: SpannerConfig): Spanner {
    val credentials = config.credentials ?:
      if (config.emulator.enabled) NoCredentials.getInstance()
      else ServiceAccountCredentials.getApplicationDefault()

    var builder = SpannerOptions.newBuilder()
      .setProjectId(config.project_id)

    if (config.emulator.enabled) {
      builder = builder
        .setCredentials(NoCredentials.getInstance())
        .setEmulatorHost("${config.emulator.hostname}:${config.emulator.port}")
    } else {
      builder = builder
        .setCredentials(credentials)
        .setHost(config.transport.host)
        .setTransportOptions(
          HttpTransportOptions.newBuilder()
            .setConnectTimeout(config.transport.connect_timeout_ms)
            .setReadTimeout(config.transport.read_timeout_ms)
            .build()
        )
    }

    return builder.build().service
  }
}
