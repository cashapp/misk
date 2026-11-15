package misk.clustering.fake.lease

import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.lease.LeaseService
import misk.testing.TestFixture
import wisp.lease.LeaderLeaseManager
import wisp.lease.LeaseManager
import wisp.lease.LoadBalancedLeaseManager

/** [FakeLeaseModule] installs support for leasing using fakes */
class FakeLeaseModule : KAbstractModule() {
  override fun configure() {
    bind<LeaseManager>().to<FakeLeaseManager>()
    bind<LeaderLeaseManager>().to<FakeLeaderLeaseManager>()
    bind<LoadBalancedLeaseManager>().to<FakeLoadBalancedLeaseManager>()
    multibind<TestFixture>().to<FakeLeaseManager>()
    multibind<TestFixture>().to<FakeLeaderLeaseManager>()
    multibind<TestFixture>().to<FakeLoadBalancedLeaseManager>()
    install(ServiceModule<LeaseService>())
  }
}
