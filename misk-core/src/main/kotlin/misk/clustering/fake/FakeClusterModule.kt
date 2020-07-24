package misk.clustering.fake

import misk.ServiceModule
import misk.clustering.Cluster
import misk.clustering.ClusterService
import misk.inject.KAbstractModule

/** [FakeClusterModule] installs fake implementations of the clustering primitives for use in tests */
class FakeClusterModule : KAbstractModule() {
  override fun configure() {
    bind<Cluster>().to<FakeCluster>()
    bind<ClusterService>().to<FakeCluster>()
    install(ServiceModule<ClusterService>())
  }
}
