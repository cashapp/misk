package wisp.lease

import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatThrownBy
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
    assertThat(otherLease.isHeld()).isTrue()
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

  @Test
  fun acquireWithWaitOptionsUsesFakeLeaseState() {
    val lease = leaseManager.requestLease("my-lease")

    assertThat(lease.acquire(AcquireOptions(wait = WaitMode.WaitForLeaseDuration))).isTrue()

    leaseManager.markLeaseHeldElsewhere("my-lease")
    assertThat(lease.acquire(AcquireOptions(wait = WaitMode.WaitUpTo(Duration.ofSeconds(1))))).isFalse()
  }

  @Test
  fun unsupportedWaitBehaviorCanFallbackToNonBlockingAcquire() {
    val lease = NonBlockingOnlyLease(acquires = true)

    assertThatThrownBy { lease.acquire(AcquireOptions(wait = WaitMode.WaitForLeaseDuration)) }
      .isInstanceOf(UnsupportedOperationException::class.java)

    assertThat(
        lease.acquire(
          AcquireOptions(
            wait = WaitMode.WaitForLeaseDuration,
            unsupportedWaitBehavior = UnsupportedWaitBehavior.FallbackToNonBlocking,
          )
        )
      )
      .isTrue()
  }

  @Test
  fun waitUpToRequiresNonNegativeTimeout() {
    assertThatIllegalArgumentException()
      .isThrownBy { WaitMode.WaitUpTo(Duration.ofMillis(-1)) }
      .withMessage("timeout must be non-negative")
  }

  private class NonBlockingOnlyLease(private val acquires: Boolean) : Lease {
    override val name = "non-blocking-only"

    override fun shouldHold() = true

    override fun isHeld() = acquires

    override fun checkHeld() = isHeld()

    override fun checkHeldElsewhere() = !isHeld()

    override fun acquire() = acquires

    override fun release() = false

    override fun release(lazy: Boolean) = release()

    override fun addListener(listener: Lease.StateChangeListener) {}
  }
}
