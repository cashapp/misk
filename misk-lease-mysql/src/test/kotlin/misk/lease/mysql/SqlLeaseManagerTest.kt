package misk.lease.mysql

import java.sql.SQLException
import java.time.Duration
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
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
    assertThat(leaseA.checkHeld()).isTrue()

    val leaseA2 = leaseManager.requestLease("a")
    assertThat(leaseA2).isNotNull()
    assertThat(leaseA2.checkHeld()).isFalse()
  }

  /**
   * Verifies that a lease can be acquired again after it is released.
   */
  @Test
  fun leaseCanBeAcquiredAfterItIsReleased() {
    val leaseA = leaseManager.requestLease("a")
    assertThat(leaseA).isNotNull()
    assertThat(leaseA.checkHeld()).isTrue()
    leaseA!!.release()

    val leaseA2 = leaseManager.requestLease("a")
    assertThat(leaseA2).isNotNull()
    assertThat(leaseA2.checkHeld()).isTrue()
  }

  /**
   * Verifies that multiple leases with different names can be acquired independently.
   */
  @Test
  fun multipleLeasesCanBeAcquiredIndependently() {
    val leaseA = leaseManager.requestLease("a")
    assertThat(leaseA).isNotNull()
    assertThat(leaseA.checkHeld()).isTrue()

    val leaseB = leaseManager.requestLease("b")
    assertThat(leaseB).isNotNull()
    assertThat(leaseB.checkHeld()).isTrue()
  }

  /**
   * Verifies that a lease cannot be acquired again immediately after the lease duration elapses.
   */
  @Test
  fun leaseCannotBeAcquiredImmediatelyBeforeDurationElapses() {
    val leaseA = leaseManager.requestLease("a")
    assertThat(leaseA).isNotNull()
    assertThat(leaseA.checkHeld()).isTrue()

    // Advance clock exactly to the lease duration
    clock.add(Duration.ofSeconds(LEASE_DURATION_SECONDS))

    val leaseA2 = leaseManager.requestLease("a")
    assertThat(leaseA2).isNotNull()
    assertThat(leaseA2.checkHeld()).isFalse()
  }

  /**
   * Verifies that a lease can be acquired by another requester after the lease duration has fully elapsed.
   */
  @Test
  fun leaseCanBeAcquiredAfterDurationElapses() {
    val leaseA = leaseManager.requestLease("a")
    assertThat(leaseA).isNotNull()
    assertThat(leaseA.checkHeld()).isTrue()

    // Advance clock past the lease duration
    clock.add(Duration.ofSeconds(LEASE_DURATION_SECONDS + 1))

    val leaseA2 = leaseManager.requestLease("a")
    assertThat(leaseA2).isNotNull()
    assertThat(leaseA2.checkHeld()).isTrue()
  }
}
