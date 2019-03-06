package misk.clustering.fake

import com.google.common.util.concurrent.Service
import misk.clustering.Cluster
import misk.inject.KAbstractModule

/** [FakeClusterModule] installs fake implementations of the clustering primitives for use in tests */
class FakeClusterModule : KAbstractModule() {
  override fun configure() {
    bind<Cluster>().to<FakeCluster>()
    multibind<Service>().to<FakeCluster>()
  }
}