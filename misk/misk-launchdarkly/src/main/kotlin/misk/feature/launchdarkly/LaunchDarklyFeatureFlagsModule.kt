package misk.feature.launchdarkly

import com.google.inject.Provides
import com.launchdarkly.client.LDClient
import com.launchdarkly.client.LDClientInterface
import com.launchdarkly.client.LDConfig
import misk.feature.FeatureFlags
import misk.feature.FeatureService
import misk.ServiceModule
import misk.client.HttpClientSSLConfig
import misk.config.Config
import misk.config.Secret
import misk.inject.KAbstractModule
import misk.inject.toKey
import misk.security.ssl.SslContextFactory
import misk.security.ssl.SslLoader
import java.net.URI
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
    bind<FeatureFlags>().to(key)
    bind<FeatureService>().to(key)
    install(ServiceModule(FeatureService::class.toKey(qualifier)))
  }

  @Provides
  fun provideLaunchDarklyClient(
    sslLoader: SslLoader,
    sslContextFactory: SslContextFactory
  ): LDClientInterface {
    val baseUri = URI.create(config.base_uri)
    val ldConfig = LDConfig.Builder()
        // Set wait to 0 to not block here. Block in service initialization instead.
        .startWaitMillis(0)
        // Don't send any attributes to LaunchDarkly in events. Trades off convenience in the
        // UI for better privacy.
        .allAttributesPrivate(true)
        .baseURI(baseUri)
        .streamURI(baseUri)
        .eventsURI(baseUri)

    config.ssl?.let {
      val trustStore = sslLoader.loadTrustStore(config.ssl.trust_store)!!
      val trustManagers = sslContextFactory.loadTrustManagers(trustStore.keyStore)
      val x509TrustManager = trustManagers.mapNotNull { it as? X509TrustManager }.firstOrNull()
          ?: throw IllegalStateException("no x509 trust manager in ${it.trust_store}")
      val sslContext = sslContextFactory.create(it.cert_store, it.trust_store)
      ldConfig.sslSocketFactory(sslContext.socketFactory, x509TrustManager)
    }

    return LDClient(config.sdk_key.value.trim(), ldConfig.build())
  }
}

data class LaunchDarklyConfig(
  val sdk_key: Secret<String>,
  val base_uri: String,
  val ssl: HttpClientSSLConfig? = null
) : Config
