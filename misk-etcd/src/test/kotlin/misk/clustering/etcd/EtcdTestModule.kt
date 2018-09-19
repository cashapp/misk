package misk.clustering.etcd

import com.coreos.jetcd.Client
import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import com.google.inject.Key
import misk.DependentService
import misk.clustering.Cluster
import misk.clustering.fake.FakeCluster
import misk.clustering.leasing.LeaseManager
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.service.ServiceTestingModule
import javax.inject.Singleton

class EtcdTestModule : KAbstractModule() {
  override fun configure() {
    bind<Client>().toProvider(EtcdClientProvider::class.java)
    bind<LeaseManager>().to<EtcdLeaseManager>()
    bind<EtcdConfig>().toInstance(sharedCluster.config)
    bind<Cluster>().to<FakeCluster>()

    multibind<Service>().to<FakeCluster>()
    multibind<Service>().to<StartEtcdService>()
    install(ServiceTestingModule.withExtraDependencies<EtcdLeaseManager>(startEtcdServiceKey))
  }

  @Singleton
  private class StartEtcdService : AbstractIdleService(), DependentService {
    override val consumedKeys: Set<Key<*>> get() = setOf()
    override val producedKeys: Set<Key<*>> get() = setOf(startEtcdServiceKey)

    override fun startUp() {
      sharedCluster.start()
    }

    override fun shutDown() {
      // NB(mmihic): Don't shutdown the shared cluster - this is expensive and causes races
    }
  }

  companion object {
    // Use a single cluster for all tests in the VM, with startup tasks to cleanup before and after
    private val sharedCluster = DockerEtcdCluster(28000)

    // Startup service key
    private val startEtcdServiceKey = keyOf<StartEtcdService>()
  }
}