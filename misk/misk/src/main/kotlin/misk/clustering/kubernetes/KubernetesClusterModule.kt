package misk.clustering.kubernetes

import com.google.common.util.concurrent.Service
import misk.clustering.Cluster
import misk.clustering.DefaultCluster
import misk.inject.KAbstractModule
import misk.inject.asSingleton

/** [KubernetesClusterModule] installs cluster support based on Kubernetes */
class KubernetesClusterModule : KAbstractModule() {
  override fun configure() {
    bind<Cluster>().to<DefaultCluster>()
    bind<DefaultCluster>().toProvider(KubernetesClusterProvider::class.java).asSingleton()
    multibind<Service>().to<KubernetesClusterWatcher>()
  }
}