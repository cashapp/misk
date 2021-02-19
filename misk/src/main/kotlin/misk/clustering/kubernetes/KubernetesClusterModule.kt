package misk.clustering.kubernetes

import misk.ServiceModule
import misk.clustering.Cluster
import misk.clustering.ClusterService
import misk.clustering.DefaultCluster
import misk.inject.KAbstractModule
import misk.inject.asSingleton

/** [KubernetesClusterModule] installs cluster support based on Kubernetes */
class KubernetesClusterModule(private val config: KubernetesConfig) : KAbstractModule() {
  override fun configure() {
    bind<KubernetesConfig>().toInstance(config)
    bind<Cluster>().to<DefaultCluster>()
    bind<ClusterService>().to<DefaultCluster>()
    bind<DefaultCluster>().toProvider(KubernetesClusterProvider::class.java).asSingleton()
    install(
      ServiceModule<KubernetesClusterWatcher>()
        .dependsOn<ClusterService>()
    )
    install(ServiceModule<ClusterService>())
  }
}
