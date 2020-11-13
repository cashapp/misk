package misk.zookeeper

import com.google.common.util.concurrent.Service
import misk.ServiceModule
import misk.clustering.zookeeper.ZookeeperConfig
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import org.apache.curator.ensemble.EnsembleProvider
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.client.HostProvider
import javax.inject.Provider
import kotlin.reflect.KClass

/**
 * Binds a [CuratorFramework] for an application to use. A [Service] is also installed to manage
 * the lifecycle of the [CuratorFramework].
 *
 * If an application needs to connect to multiple Zookeepers, an optional qualifier can be passed
 * resulting in an annotated [CuratorFramework] binding.
 *
 * ```
 * @Qualifier
 * annotation class MyZk
 *
 * install(CuratorFrameworkModule(config, MyZk::class)
 *
 * @Inject @MyZk CuratorFramework
 * ```
 */
class CuratorFrameworkModule(
  private val config: ZookeeperConfig,
  private val qualifier: KClass<out Annotation>? = null
) : KAbstractModule() {
  override fun configure() {
    val ensembleProvider = getProvider(keyOf<EnsembleProvider>(qualifier))
    val hostProvider = getProvider(keyOf<HostProvider>(qualifier))
    bind(keyOf<CuratorFramework>(qualifier))
        .toProvider(CuratorFrameworkProvider(config, ensembleProvider, hostProvider))
        .asSingleton()
    val curator = getProvider(keyOf<CuratorFramework>(qualifier))
    bind(keyOf<ZkService>(qualifier))
        .toProvider(Provider<ZkService> { ZkService(curator.get()) })
        .asSingleton()
    install(ServiceModule<ZkService>(qualifier))
  }
}
