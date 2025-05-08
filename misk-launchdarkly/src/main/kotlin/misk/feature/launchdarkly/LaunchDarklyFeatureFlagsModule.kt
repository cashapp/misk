package misk.feature.launchdarkly

import com.google.inject.Provider
import com.google.inject.Provides
import com.launchdarkly.sdk.server.Components
import com.launchdarkly.sdk.server.LDClient
import com.launchdarkly.sdk.server.LDConfig
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder.DEFAULT_CAPACITY
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder.DEFAULT_FLUSH_INTERVAL
import com.launchdarkly.sdk.server.interfaces.LDClientInterface
import com.squareup.moshi.Moshi
import io.micrometer.core.instrument.MeterRegistry
import jakarta.inject.Singleton
import misk.ReadyService
import misk.ServiceModule
import misk.client.HttpClientSSLConfig
import misk.config.Redact
import misk.feature.DynamicConfig
import misk.feature.FeatureFlags
import misk.feature.FeatureService
import misk.inject.KAbstractModule
import misk.resources.ResourceLoader
import misk.security.ssl.SslContextFactory
import misk.security.ssl.SslLoader
import wisp.config.Config
import wisp.logging.getLogger
import java.net.URI
import java.time.Duration
import javax.net.ssl.X509TrustManager
import kotlin.reflect.KClass

/**
 * Binds a [FeatureFlags] backed by LaunchDarkly (https://launchdarkly.com).
 */
class LaunchDarklyModule @JvmOverloads constructor(
  private val config: LaunchDarklyConfig,
  private val qualifier: KClass<out Annotation>? = null,
) : KAbstractModule() {
  override fun configure() {
    bind<wisp.feature.FeatureFlags>().to<wisp.launchdarkly.LaunchDarklyFeatureFlags>()
    bind<FeatureFlags>().to<LaunchDarklyFeatureFlags>()
    bind<FeatureService>().to<LaunchDarklyFeatureFlags>()
    bind<DynamicConfig>().to<LaunchDarklyDynamicConfig>()
    install(ServiceModule<FeatureService>().enhancedBy<ReadyService>())
  }

  @Provides
  @Singleton
  internal fun wispLaunchDarkly(
    ldClientInterface: Provider<LDClientInterface>,
    moshi: Moshi,
    meterRegistry: MeterRegistry,
  ): wisp.launchdarkly.LaunchDarklyFeatureFlags {
    val ldClient = lazy { ldClientInterface.get() }
    return wisp.launchdarkly.LaunchDarklyFeatureFlags(ldClient, moshi, meterRegistry)
  }

  @Provides
  @Singleton
  internal fun providesLdClientInterface(
    sslLoader: SslLoader,
    sslContextFactory: SslContextFactory,
    resourceLoader: ResourceLoader,
  ): LDClientInterface {
    // TODO: This shouldn't exist. We should not be exposing LDClientInterface and the only users of this are
    //   apps who're installing this module but not even using the misk or wisp LaunchDarklyFeatureFlags.
    try {
      logger.debug("Starting LD client configuration...")

      val ldConfig = LDConfig.Builder()
        // Set wait to 0 to not block here. Block in service initialization instead.
        .startWait(Duration.ofMillis(0))
        .dataSource(Components.streamingDataSource())
        .events(
          Components.sendEvents()
            .capacity(config.event_capacity)
            .flushInterval(config.flush_interval)
        )
        .offline(config.offline)

      logger.debug("Configuring service endpoints...")
      if (config.use_relay_proxy) {
        logger.debug("Using relay proxy: ${config.base_uri}")
        ldConfig.serviceEndpoints(
          Components.serviceEndpoints().relayProxy(URI.create(config.base_uri))
        )
      }

      config.ssl?.let {
        logger.debug("Configuring SSL context...")
        val trustStore = sslLoader.loadTrustStore(config.ssl.trust_store)!!
        val trustManagers = sslContextFactory.loadTrustManagers(trustStore.keyStore)
        val x509TrustManager = trustManagers.mapNotNull { it as? X509TrustManager }.firstOrNull()
          ?: throw IllegalStateException("no x509 trust manager in ${it.trust_store}")
        val sslContext = sslContextFactory.create(it.cert_store, it.trust_store)
        ldConfig.http(
          Components.httpConfiguration().sslSocketFactory(sslContext.socketFactory, x509TrustManager)
        )
      }

      // Construct LDClient lazily to avoid making network calls until LaunchDarklyFeatureFlags.startup() is called.
      return LDClient(resourceLoader.requireUtf8(config.sdk_key).trim(), ldConfig.build())
    } catch (e: Exception) {
      logger.debug("Error while creating LaunchDarkly client: ${e.message}", e)
      throw e
    }
  }

  companion object {
    private val logger = getLogger<LaunchDarklyModule>()
  }
}

data class LaunchDarklyConfig @JvmOverloads constructor(
  @Redact
  val sdk_key: String,
  val base_uri: String,
  val use_relay_proxy: Boolean = true,
  val ssl: HttpClientSSLConfig? = null,
  val event_capacity: Int = DEFAULT_CAPACITY,
  val flush_interval: Duration = DEFAULT_FLUSH_INTERVAL,
  val offline: Boolean = false,
) : Config
