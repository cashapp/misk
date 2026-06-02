package misk.clustering.kubernetes

import com.google.inject.Provider
import jakarta.inject.Inject
import misk.clustering.Cluster
import misk.clustering.DefaultCluster

internal class KubernetesClusterProvider @Inject internal constructor(private val config: KubernetesConfig) :
  Provider<DefaultCluster> {
  override fun get() = DefaultCluster(Cluster.Member(config.my_pod_name, config.my_pod_ip))
}
