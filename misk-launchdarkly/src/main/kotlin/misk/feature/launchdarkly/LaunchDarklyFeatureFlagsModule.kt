package misk.feature.launchdarkly

import com.google.inject.Provides
import com.launchdarkly.sdk.server.interfaces.LDClientInterface
import misk.ServiceModule
import misk.feature.FeatureService
import misk.inject.KAbstractModule
import misk.inject.toKey
import wisp.feature.DynamicConfig
import wisp.feature.FeatureFlags
import wisp.launchdarkly.LaunchDarklyClient
import wisp.launchdarkly.LaunchDarklyConfig
import wisp.resources.ResourceLoader
import wisp.security.ssl.SslContextFactory
import wisp.security.ssl.SslLoader
import javax.inject.Provider
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
    return LaunchDarklyClient.createLaunchDarklyClient(
      config,
      sslLoader,
      sslContextFactory,
      resourceLoader
    )
  }

}

