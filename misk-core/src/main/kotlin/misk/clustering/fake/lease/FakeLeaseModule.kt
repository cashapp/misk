package misk.clustering.fake.lease

import misk.clustering.lease.LeaseManager
import misk.inject.KAbstractModule

/** [FakeLeaseModule] installs support for leasing using fakes */
class FakeLeaseModule : KAbstractModule() {
  override fun configure() {
    bind<LeaseManager>().to<FakeLeaseManager>()
  }
}
