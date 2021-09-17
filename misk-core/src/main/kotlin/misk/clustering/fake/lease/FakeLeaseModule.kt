package misk.clustering.fake.lease

import misk.inject.KAbstractModule
import wisp.lease.LeaseManager

/** [FakeLeaseModule] installs support for leasing using fakes */
class FakeLeaseModule : KAbstractModule() {
  override fun configure() {
    bind<LeaseManager>().to<FakeLeaseManager>()
  }
}
