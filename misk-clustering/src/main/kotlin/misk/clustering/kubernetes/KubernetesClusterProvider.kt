package misk.clustering.kubernetes

import misk.clustering.Cluster
import misk.clustering.DefaultCluster
import javax.inject.Inject
import javax.inject.Provider

internal class KubernetesClusterProvider @Inject internal constructor(
  private val config: KubernetesConfig
) : Provider<DefaultCluster> {
  override fun get() = DefaultCluster(Cluster.Member(config.my_pod_name, config.my_pod_ip))
}
