package misk.cloud.gcp.spanner

import com.google.api.gax.retrying.RetrySettings
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.NoCredentials
import com.google.cloud.spanner.Spanner
import com.google.cloud.spanner.SpannerOptions
import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Provides
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.logging.getLogger
import org.threeten.bp.Duration

/**
 * [GoogleSpannerModule] provides a Google Spanner client for your app.
 *
 * For testing, install the emulator
 */
class GoogleSpannerModule(private val spannerConfig: SpannerConfig) : KAbstractModule() {
  override fun configure() {
    bind<SpannerConfig>().toInstance(spannerConfig)
    install(ServiceModule<GoogleSpannerService>().enhancedBy<ReadyService>())
  }

  @Provides
  @Singleton
  fun provideCloudSpanner(config: SpannerConfig): Spanner {
    val credentials =
      config.credentials
        ?: if (config.emulator.enabled) NoCredentials.getInstance()
        else ServiceAccountCredentials.getApplicationDefault()

    val retrySettings = RetrySettings.newBuilder()

    config.max_attempts?.let { retrySettings.setMaxAttempts(it) }
    config.total_timeout_s?.let { retrySettings.setTotalTimeout(Duration.ofSeconds(it)) }
    config.initial_retry_delay_ms?.let { retrySettings.setInitialRetryDelay(Duration.ofMillis(it)) }
    config.initial_rpc_timeout_s?.let { retrySettings.setInitialRpcTimeout(Duration.ofSeconds(it)) }
    config.max_retry_delay_s?.let { retrySettings.setMaxRetryDelay(Duration.ofSeconds(it)) }
    config.max_rpc_timeout_s?.let { retrySettings.setMaxRpcTimeout(Duration.ofSeconds(it)) }
    config.rpc_timeout_multipler?.let { retrySettings.setRpcTimeoutMultiplier(it) }
    config.retry_delay_multiplier?.let { retrySettings.setRetryDelayMultiplier(it) }

    var builder = SpannerOptions.newBuilder().setProjectId(config.project_id)

    builder.spannerStubSettingsBuilder.executeSqlSettings().setRetrySettings(retrySettings.build())

    if (config.emulator.enabled) {
      builder =
        builder
          .setCredentials(NoCredentials.getInstance())
          .setEmulatorHost("${config.emulator.hostname}:${config.emulator.port}")
    } else {
      builder = builder.setCredentials(credentials)
    }

    return builder.build().service
  }
}

@Singleton
class GoogleSpannerService @Inject constructor(private val spanner: Spanner) : AbstractIdleService() {
  companion object {
    private val log = getLogger<GoogleSpannerService>()
  }

  override fun startUp() {
    if (spanner.isClosed) {
      throw IllegalStateException(
        "Current Spanner client session has been terminated. " + "Restart the application to get a new one."
      )
    }
    log.info { "Spanner client initialized for project ${spanner.options.projectId}." }
  }

  override fun shutDown() {
    spanner.close()
    log.info { "Spanner client sessions safely terminated." }
  }
}
