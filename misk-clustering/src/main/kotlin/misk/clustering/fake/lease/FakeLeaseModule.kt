package misk.clustering.fake.lease

import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.lease.LeaseService
import misk.testing.TestFixture
import wisp.lease.LeaseManager

/** [FakeLeaseModule] installs support for leasing using fakes */
class FakeLeaseModule : KAbstractModule() {
  override fun configure() {
    bind<LeaseManager>().to<FakeLeaseManager>()
    multibind<TestFixture>().to<FakeLeaseManager>()
    install(ServiceModule<LeaseService>())
  }
}
