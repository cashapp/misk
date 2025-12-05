package misk.lease.mysql

import java.time.Duration
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.lease.mysql.SqlLeaseTestingModule.Companion.LEASE_DURATION_SECONDS
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING
import wisp.lease.LeaseManager

@MiskTest(startService = true)
class SqlLeaseManagerTest {
  @MiskTestModule val module = object : KAbstractModule() {
    override fun configure() {
      install(DeploymentModule(TESTING))
      install(MiskTestingServiceModule())
      install(SqlLeaseTestingModule())
    }
  }

  @Inject lateinit var leaseManager: LeaseManager
  @Inject lateinit var clock: FakeClock

  /**
   * Verifies that a lease cannot be acquired by a second requester while it is held.
   */
  @Test
  fun leaseCannotBeAcquiredWhenItIsHeld() {
    val leaseA = leaseManager.requestLease("a")
    assertThat(leaseA).isNotNull()
    assertThat(leaseA.isHeld()).isTrue()
    assertThat(leaseA.checkHeld()).isTrue()

    val leaseA2 = leaseManager.requestLease("a")
    assertThat(leaseA2).isNotNull()
    assertThat(leaseA2.isHeld()).isFalse()
    assertThat(leaseA2.checkHeld()).isFalse()
  }

  /**
   * Verifies that a lease can be acquired again after it is released.
   */
  @Test
  fun leaseCanBeAcquiredAfterItIsReleased() {
    val leaseA = leaseManager.requestLease("a")
    assertThat(leaseA).isNotNull()
    assertThat(leaseA.isHeld()).isTrue()
    assertThat(leaseA.checkHeld()).isTrue()
    leaseA!!.release()

    val leaseA2 = leaseManager.requestLease("a")
    assertThat(leaseA2).isNotNull()
    assertThat(leaseA2.isHeld()).isTrue()
    assertThat(leaseA2.checkHeld()).isTrue()
  }

  /**
   * Verifies that multiple leases with different names can be acquired independently.
   */
  @Test
  fun multipleLeasesCanBeAcquiredIndependently() {
    val leaseA = leaseManager.requestLease("a")
    assertThat(leaseA).isNotNull()
    assertThat(leaseA.isHeld()).isTrue()
    assertThat(leaseA.checkHeld()).isTrue()

    val leaseB = leaseManager.requestLease("b")
    assertThat(leaseB).isNotNull()
    assertThat(leaseB.isHeld()).isTrue()
    assertThat(leaseB.checkHeld()).isTrue()
  }

  /**
   * Verifies that a lease cannot be acquired again immediately after the lease duration elapses.
   */
  @Test
  @OptIn(ExperimentalMiskApi::class)
  fun leaseCannotBeAcquiredImmediatelyBeforeDurationElapses() {
    val sqlLeaseManager = leaseManager as SqlLeaseManager
    // Use explicit 60 second duration for this test
    val leaseA = sqlLeaseManager.requestLease("test-lease", Duration.ofSeconds(LEASE_DURATION_SECONDS))
    assertThat(leaseA).isNotNull()
    assertThat(leaseA.isHeld()).isTrue()
    assertThat(leaseA.checkHeld()).isTrue()

    // Advance clock exactly to the lease duration (60 seconds)
    clock.add(Duration.ofSeconds(LEASE_DURATION_SECONDS))

    val leaseA2 = sqlLeaseManager.requestLease("test-lease", Duration.ofSeconds(LEASE_DURATION_SECONDS))
    assertThat(leaseA2).isNotNull()
    assertThat(leaseA2.isHeld()).isFalse()
    assertThat(leaseA2.checkHeld()).isFalse()
  }

  /**
   * Verifies that a lease can be acquired by another requester after the lease duration has fully elapsed.
   */
  @Test
  @OptIn(ExperimentalMiskApi::class)
  fun leaseCanBeAcquiredAfterDurationElapses() {
    val sqlLeaseManager = leaseManager as SqlLeaseManager
    // Use explicit 60 second duration for this test
    val leaseA = sqlLeaseManager.requestLease("test-lease", Duration.ofSeconds(LEASE_DURATION_SECONDS))
    assertThat(leaseA).isNotNull()
    assertThat(leaseA.isHeld()).isTrue()
    assertThat(leaseA.checkHeld()).isTrue()

    // Advance clock past the lease duration (60 seconds + 1)
    clock.add(Duration.ofSeconds(LEASE_DURATION_SECONDS + 1))

    val leaseA2 = sqlLeaseManager.requestLease("test-lease", Duration.ofSeconds(LEASE_DURATION_SECONDS))
    assertThat(leaseA2).isNotNull()
    assertThat(leaseA2.isHeld()).isTrue()
    assertThat(leaseA2.checkHeld()).isTrue()
  }

  /**
   * Test the call-site duration configuration approach.
   */
  @Test
  @OptIn(ExperimentalMiskApi::class)
  fun testCallSiteDurationConfiguration() {
    val sqlLeaseManager = leaseManager as SqlLeaseManager

    // Test default duration (300 seconds)
    val defaultLease = sqlLeaseManager.requestLease("default-lease")
    assertThat(defaultLease.isHeld()).isTrue()
    assertThat(defaultLease.checkHeld()).isTrue()

    // Test explicit short duration (30 seconds)
    val shortLease = sqlLeaseManager.requestLease("short-lease", Duration.ofSeconds(30))
    assertThat(shortLease.isHeld()).isTrue()
    assertThat(shortLease.checkHeld()).isTrue()

    // Test explicit long duration (10 minutes)
    val longLease = sqlLeaseManager.requestLease("long-lease", Duration.ofMinutes(10))
    assertThat(longLease.isHeld()).isTrue()
    assertThat(longLease.checkHeld()).isTrue()

    // Advance clock by 31 seconds - short lease should expire, others should still be held
    clock.add(Duration.ofSeconds(31))

    // Short lease should be expired and acquirable by someone else
    val shortLease2 = sqlLeaseManager.requestLease("short-lease", Duration.ofSeconds(30))
    assertThat(shortLease2.isHeld()).isTrue()
    assertThat(shortLease2.checkHeld()).isTrue()

    // Default and long leases should still be held
    assertThat(defaultLease.isHeld()).isTrue()
    assertThat(defaultLease.checkHeld()).isTrue()
    assertThat(longLease.isHeld()).isTrue()
    assertThat(longLease.checkHeld()).isTrue()
  }
}
