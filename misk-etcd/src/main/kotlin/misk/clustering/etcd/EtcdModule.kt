package misk.clustering.etcd

import com.coreos.jetcd.Client
import com.google.common.util.concurrent.Service
import com.google.inject.Key
import misk.clustering.leasing.LeaseManager
import misk.inject.KAbstractModule
import misk.inject.keyOf

class EtcdModule : KAbstractModule() {
  override fun configure() {
    multibind<Service>().to<EtcdLeaseManager>()
    bind<LeaseManager>().to<EtcdLeaseManager>()
    bind<Client>().toProvider(EtcdClientProvider::class.java)
  }

  companion object {
    val leaseManagerKey: Key<*> = keyOf<EtcdLeaseManager>()
  }
}