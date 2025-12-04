package misk.clustering.fake.lease

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class FakeLeaseManagerTest {

  private var leaseManager = FakeLeaseManager()

  @Test
  fun leaseRespectsAuthority() {
    val lease = leaseManager.requestLease("my-lease")
    val otherLease = leaseManager.requestLease("my-other-lease")

    // leases are held by the current process by default
    assertThat(lease.isHeld()).isTrue()
    assertThat(lease.checkHeld()).isTrue()
    assertThat(otherLease.checkHeld()).isTrue()

    leaseManager.markLeaseHeldElsewhere("my-lease")
    assertThat(lease.isHeld()).isFalse()
    assertThat(lease.checkHeld()).isFalse()
    assertThat(lease.acquire()).isFalse()
    assertThat(otherLease.isHeld()).isTrue()
    assertThat(otherLease.checkHeld()).isTrue()

    leaseManager.markLeaseHeld("my-lease")
    assertThat(lease.isHeld()).isTrue()
    assertThat(lease.checkHeld()).isTrue()
    assertThat(otherLease.isHeld()).isTrue()
    assertThat(otherLease.checkHeld()).isTrue()
  }
}
