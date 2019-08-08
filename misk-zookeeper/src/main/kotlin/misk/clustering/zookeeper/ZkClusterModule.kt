package misk.clustering.zookeeper

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Provides
import misk.ServiceModule
import misk.clustering.Cluster
import misk.clustering.DefaultCluster
import misk.clustering.kubernetes.KubernetesConfig
import misk.config.AppName
import misk.inject.KAbstractModule
import misk.zookeeper.ZkClientFactory
import misk.zookeeper.ZkService
import org.apache.curator.framework.CuratorFramework
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Singleton

class ZkClusterModule(private val self: Cluster.Member) : KAbstractModule() {

  constructor(config: KubernetesConfig) :
      this(Cluster.Member(config.my_pod_name, config.my_pod_ip))

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
  annotation class ZkCluster

  override fun configure() {
    install(ServiceModule<ZkClusterWatcher>()
        .dependsOn<ZkService>(ForZkLease::class))
    install(ServiceModule<ZkClusterJoiner>()
        .dependsOn<ZkService>(ForZkLease::class))

    install(ServiceModule<DefaultCluster>(ZkCluster::class))
  }

  @Provides @Singleton @ForZkLease
  fun zkClientFactory(@AppName appName: String, @ForZkLease curator: CuratorFramework) =
      ZkClientFactory(appName, curator)

  @Provides
  @Singleton
  fun clusterWatcher(
    @ForZkLease clientFactory: ZkClientFactory,
    @ZkCluster defaultCluster: DefaultCluster
  ): ZkClusterWatcher = ZkClusterWatcher(
      clientFactory = clientFactory,
      onChange = { added, removed -> defaultCluster.clusterChanged(added, removed) })

  @Provides
  @Singleton
  fun zkClusterJoiner(
    @ForZkLease clientFactory: ZkClientFactory,
    serviceManager: Provider<ServiceManager>
  ) = ZkClusterJoiner(
      self = self,
      clientFactory = clientFactory,
      serviceManager = serviceManager
  )

  @Provides
  @Singleton
  @ZkCluster
  fun zkDefaultCluster(): DefaultCluster {
    return DefaultCluster(self = self)
  }

  @Provides
  @Singleton
  @ZkCluster
  fun zkCluster(@ZkCluster defaultCluster: DefaultCluster): Cluster = defaultCluster
}
