package misk.zookeeper

import com.google.common.util.concurrent.Service
import com.google.inject.Key
import misk.clustering.zookeeper.ZookeeperConfig
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import org.apache.curator.framework.CuratorFramework
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
 * install(ZookeeperModule(config, MyZk::class)
 *
 * @Inject @MyZk CuratorFramework
 * ```
 */
class ZookeeperModule(
  private val config: ZookeeperConfig,
  private val qualifier: KClass<out Annotation>?
) : KAbstractModule() {
  override fun configure() {
    bind(keyOf<CuratorFramework>(qualifier)).toProvider(CuratorFrameworkProvider(config)).asSingleton()
    val curator = getProvider(keyOf<CuratorFramework>(qualifier))
    multibind<Service>().toProvider(object : Provider<ZkService> {
      override fun get(): ZkService {
        return ZkService(curator.get(), qualifier)
      }
    }).asSingleton()
  }
}
