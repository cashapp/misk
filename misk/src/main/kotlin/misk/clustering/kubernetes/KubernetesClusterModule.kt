package misk.clustering.kubernetes

import com.google.common.util.concurrent.Service
import misk.clustering.Cluster
import misk.inject.KAbstractModule

/** [KubernetesClusterModule] installs cluster support based on Kubernetes */
class KubernetesClusterModule : KAbstractModule() {
  override fun configure() {
    bind<Cluster>().to<KubernetesClusterService>()
    multibind<Service>().to<KubernetesClusterService>()
    multibind<Service>().to<KubernetesClusterWatcher>()
  }
}