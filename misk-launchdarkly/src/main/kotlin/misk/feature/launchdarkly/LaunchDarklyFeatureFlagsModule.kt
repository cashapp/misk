package misk.feature.launchdarkly

import com.google.inject.Provider
import com.google.inject.Provides
import com.launchdarkly.sdk.server.Components
import com.launchdarkly.sdk.server.LDClient
import com.launchdarkly.sdk.server.LDConfig
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
import java.net.URI
import java.time.Duration
import javax.net.ssl.X509TrustManager

/**
 * Binds a [FeatureFlags] backed by LaunchDarkly (https://launchdarkly.com).
 */
class LaunchDarklyModule(
  private val config: LaunchDarklyConfig,
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
  fun wispLaunchDarkly(
    ldClient: Provider<LDClientInterface>,
    moshi: Moshi,
    meterRegistry: MeterRegistry,
  ): wisp.launchdarkly.LaunchDarklyFeatureFlags {
    // Use providers and lazy injection to avoid the LDClientInterface from being
    // constructed which immediately starts the network calls.
    return wisp.launchdarkly.LaunchDarklyFeatureFlags(lazy { ldClient.get() }, moshi, meterRegistry)
  }

  @Provides
  @Singleton
  fun provideLaunchDarklyClient(
    sslLoader: SslLoader,
    sslContextFactory: SslContextFactory,
    resourceLoader: ResourceLoader,
  ): LDClientInterface {
    val baseUri = URI.create(config.base_uri)
    val ldConfig = LDConfig.Builder()
      // Set wait to 0 to not block here. Block in service initialization instead.
      .startWait(Duration.ofMillis(0))
      .dataSource(Components.streamingDataSource().baseURI(baseUri))
      .events(Components.sendEvents().baseURI(baseUri))

    config.ssl?.let {
      val trustStore = sslLoader.loadTrustStore(config.ssl.trust_store)!!
      val trustManagers = sslContextFactory.loadTrustManagers(trustStore.keyStore)
      val x509TrustManager = trustManagers.mapNotNull { it as? X509TrustManager }.firstOrNull()
        ?: throw IllegalStateException("no x509 trust manager in ${it.trust_store}")
      val sslContext = sslContextFactory.create(it.cert_store, it.trust_store)
      ldConfig.http(
        Components.httpConfiguration().sslSocketFactory(sslContext.socketFactory, x509TrustManager)
      )
    }

    return LDClient(resourceLoader.requireUtf8(config.sdk_key).trim(), ldConfig.build())
  }
}

data class LaunchDarklyConfig @JvmOverloads constructor(
  @Redact
  val sdk_key: String,
  val base_uri: String,
  val ssl: HttpClientSSLConfig? = null,
) : Config
