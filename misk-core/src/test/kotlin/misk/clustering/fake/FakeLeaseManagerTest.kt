package misk.clustering.fake

import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.clustering.fake.lease.FakeLeaseManager
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class FakeLeaseManagerTest {
  @MiskTestModule val module = Modules.combine(
      MiskTestingServiceModule(),
      FakeClusterModule()
  )

  @Inject private lateinit var leaseManager: FakeLeaseManager

  @Test fun leaseRespectsAuthority() {
    val lease = leaseManager.requestLease("my-lease")
    val otherLease = leaseManager.requestLease("my-other-lease")

    // leases are held by the current process by default
    assertThat(lease.checkHeld()).isTrue()
    assertThat(otherLease.checkHeld()).isTrue()

    leaseManager.markLeaseHeldElsewhere("my-lease")
    assertThat(lease.checkHeld()).isFalse()
    assertThat(otherLease.checkHeld()).isTrue()

    leaseManager.markLeaseHeld("my-lease")
    assertThat(lease.checkHeld()).isTrue()
    assertThat(otherLease.checkHeld()).isTrue()
  }
}
