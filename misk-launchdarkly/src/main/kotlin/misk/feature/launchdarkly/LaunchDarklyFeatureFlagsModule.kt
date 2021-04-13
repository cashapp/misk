package misk.feature.launchdarkly

import com.google.inject.Provides
import com.launchdarkly.sdk.server.Components
import com.launchdarkly.sdk.server.LDClient
import com.launchdarkly.sdk.server.LDConfig
import com.launchdarkly.sdk.server.interfaces.LDClientInterface
import misk.ServiceModule
import misk.client.HttpClientSSLConfig
import misk.feature.DynamicConfig
import misk.feature.FeatureFlags
import misk.feature.FeatureService
import misk.inject.KAbstractModule
import misk.inject.toKey
import misk.resources.ResourceLoader
import misk.security.ssl.SslContextFactory
import misk.security.ssl.SslLoader
import wisp.config.Config
import java.net.URI
import java.time.Duration
import javax.inject.Provider
import javax.net.ssl.X509TrustManager
import kotlin.reflect.KClass

/**
 * Binds a [FeatureFlags] backed by LaunchDarkly (https://launchdarkly.com).
 */
class LaunchDarklyModule(
  private val config: LaunchDarklyConfig,
  private val qualifier: KClass<out Annotation>? = null
) : KAbstractModule() {
  override fun configure() {
    val key = LaunchDarklyFeatureFlags::class.toKey(qualifier)
    bind(FeatureFlags::class.toKey(qualifier)).to(key)
    bind(FeatureService::class.toKey(qualifier)).to(key)
    val featureFlagsProvider = getProvider(key)
    bind(DynamicConfig::class.toKey(qualifier)).toProvider(
      Provider<DynamicConfig> { LaunchDarklyDynamicConfig(featureFlagsProvider.get()) })
    install(ServiceModule(FeatureService::class.toKey(qualifier)))
  }

  @Provides
  fun provideLaunchDarklyClient(
    sslLoader: SslLoader,
    sslContextFactory: SslContextFactory,
    resourceLoader: ResourceLoader
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

data class LaunchDarklyConfig(
  val sdk_key: String,
  val base_uri: String,
  val ssl: HttpClientSSLConfig? = null
) : Config
