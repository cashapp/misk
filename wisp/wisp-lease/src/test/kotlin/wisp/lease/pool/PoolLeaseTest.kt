package wisp.lease.pool

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import wisp.deployment.STAGING
import wisp.deployment.TESTING
import wisp.lease.Lease
import wisp.lease.LeaseManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class PoolLeaseTest {

  @Test
  fun fakeDeploymentAndNoPoolConfigReturnsExpectedLease() {
    val testLease = TestLease(LEASE_NAME, acquire = false)
    val delegateLeaseManager = Mockito.mock(LeaseManager::class.java)
    `when`(delegateLeaseManager.requestLease(LEASE_NAME)).thenReturn(testLease)
    val poolLeaseManager = PoolLeaseManager(delegateLeaseManager, TESTING, emptyList())
    val requestedLease = poolLeaseManager.requestLease(LEASE_NAME)
    assertEquals(testLease.name, requestedLease.name)
    assertFalse(requestedLease.acquire())
    assertTrue(poolLeaseManager.isEmptyPoolLeaseMapEntry(LEASE_NAME))
    verify(delegateLeaseManager).requestLease(LEASE_NAME)
  }

  @Test
  fun fakeDeploymentAndPoolConfigReturnsExpectedLease() {
    val testLease = TestLease(LEASE_NAME, acquire = false)
    val delegateLeaseManager = Mockito.mock(LeaseManager::class.java)
    `when`(delegateLeaseManager.requestLease(LEASE_NAME)).thenReturn(testLease)
    val poolLeaseManager = PoolLeaseManager(delegateLeaseManager, TESTING, listOf(poolLeaseConfig))
    val requestedLease = poolLeaseManager.requestLease(LEASE_NAME)
    assertEquals(testLease.name, requestedLease.name)
    assertFalse(requestedLease.acquire())
    assertTrue(poolLeaseManager.isEmptyPoolLeaseMapEntry(LEASE_NAME))
    verify(delegateLeaseManager).requestLease(LEASE_NAME)
  }

  @Test
  fun realDeploymentAndNoPoolConfigReturnsExpectedLease() {
    val testLease = TestLease(LEASE_NAME, acquire = false)
    val delegateLeaseManager = Mockito.mock(LeaseManager::class.java)
    `when`(delegateLeaseManager.requestLease(LEASE_NAME)).thenReturn(testLease)
    val poolLeaseManager = PoolLeaseManager(delegateLeaseManager, STAGING, emptyList())
    val requestedLease = poolLeaseManager.requestLease(LEASE_NAME)
    assertEquals(testLease.name, requestedLease.name)
    assertFalse(requestedLease.acquire())
    assertTrue(poolLeaseManager.isEmptyPoolLeaseMapEntry(LEASE_NAME))
    verify(delegateLeaseManager).requestLease(LEASE_NAME)
  }

  @Test
  fun realDeploymentAndPoolConfigReturnsExpectedLease() {
    val testLease = TestLease(LEASE_NAME, acquire = true)
    val delegateLeaseManager = Mockito.mock(LeaseManager::class.java)
    `when`(delegateLeaseManager.requestLease(LEASE_NAME)).thenReturn(testLease)
    val poolLeaseManager = PoolLeaseManager(delegateLeaseManager, STAGING, listOf(poolLeaseConfig))
    val requestedLease = poolLeaseManager.requestLease(LEASE_NAME)
    assertEquals(testLease.name, requestedLease.name)
    assertTrue(requestedLease.acquire())
    assertFalse(poolLeaseManager.isEmptyPoolLeaseMapEntry(LEASE_NAME))
    verify(delegateLeaseManager).requestLease(LEASE_NAME)
  }

  @Test
  fun fakeDeploymentAndPoolConfigMultipleLeaseTests() {
    val testLease = TestLease(LEASE_NAME, acquire = true)
    val anotherTestLease = TestLease(ANOTHER_LEASE_NAME, acquire = true)
    val notInPoolTestLease = TestLease(NOT_IN_POOL_LEASE_NAME, acquire = true)

    val delegateLeaseManager = Mockito.mock(LeaseManager::class.java)
    `when`(delegateLeaseManager.requestLease(LEASE_NAME)).thenReturn(testLease)
    `when`(delegateLeaseManager.requestLease(ANOTHER_LEASE_NAME)).thenReturn(anotherTestLease)
    `when`(delegateLeaseManager.requestLease(NOT_IN_POOL_LEASE_NAME)).thenReturn(notInPoolTestLease)

    val poolLeaseManager = PoolLeaseManager(delegateLeaseManager, TESTING, listOf(poolLeaseConfig))
    val requestedLease = poolLeaseManager.requestLease(LEASE_NAME)
    val requestedAnotherLease = poolLeaseManager.requestLease(ANOTHER_LEASE_NAME)
    val requestedNotInPoolLease = poolLeaseManager.requestLease(NOT_IN_POOL_LEASE_NAME)

    assertEquals(testLease.name, requestedLease.name)
    assertEquals(anotherTestLease.name, requestedAnotherLease.name)
    assertEquals(notInPoolTestLease.name, requestedNotInPoolLease.name)

    assertTrue(requestedLease.acquire())
    assertTrue(requestedAnotherLease.acquire())
    assertTrue(requestedNotInPoolLease.acquire())

    assertTrue(poolLeaseManager.isEmptyPoolLeaseMapEntry(LEASE_NAME))
    assertTrue(poolLeaseManager.isEmptyPoolLeaseMapEntry(ANOTHER_LEASE_NAME))
    assertTrue(poolLeaseManager.isEmptyPoolLeaseMapEntry(NOT_IN_POOL_LEASE_NAME))

    verify(delegateLeaseManager).requestLease(LEASE_NAME)
    verify(delegateLeaseManager).requestLease(ANOTHER_LEASE_NAME)
    verify(delegateLeaseManager).requestLease(NOT_IN_POOL_LEASE_NAME)

  }

  @Test
  fun realDeploymentAndPoolConfigMultipleLeaseTests() {
    val testLease = TestLease(LEASE_NAME, acquire = true)
    val anotherTestLease = TestLease(ANOTHER_LEASE_NAME, acquire = true)
    val notInPoolTestLease = TestLease(NOT_IN_POOL_LEASE_NAME, acquire = true)

    val delegateLeaseManager = Mockito.mock(LeaseManager::class.java)
    `when`(delegateLeaseManager.requestLease(LEASE_NAME)).thenReturn(testLease)
    `when`(delegateLeaseManager.requestLease(ANOTHER_LEASE_NAME)).thenReturn(anotherTestLease)
    `when`(delegateLeaseManager.requestLease(NOT_IN_POOL_LEASE_NAME)).thenReturn(notInPoolTestLease)

    val poolLeaseManager = PoolLeaseManager(delegateLeaseManager, STAGING, listOf(poolLeaseConfig))
    val requestedLease = poolLeaseManager.requestLease(LEASE_NAME)
    val requestedAnotherLease = poolLeaseManager.requestLease(ANOTHER_LEASE_NAME)
    val requestedNotInPoolLease = poolLeaseManager.requestLease(NOT_IN_POOL_LEASE_NAME)

    assertEquals(testLease.name, requestedLease.name)
    assertEquals(anotherTestLease.name, requestedAnotherLease.name)
    assertEquals(notInPoolTestLease.name, requestedNotInPoolLease.name)

    assertTrue(requestedLease.acquire())
    // other lease in pool has been acquired, so this should be overriding the delegate value
    assertFalse(requestedAnotherLease.acquire())
    assertTrue(requestedNotInPoolLease.acquire())

    assertFalse(poolLeaseManager.isEmptyPoolLeaseMapEntry(LEASE_NAME))
    assertFalse(poolLeaseManager.isEmptyPoolLeaseMapEntry(ANOTHER_LEASE_NAME))
    assertTrue(poolLeaseManager.isEmptyPoolLeaseMapEntry(NOT_IN_POOL_LEASE_NAME))

    // release the lease in the pool so this should mean we're not tracking any lease
    requestedLease.release()
    assertTrue(poolLeaseManager.isEmptyPoolLeaseMapEntry(LEASE_NAME))
    assertTrue(poolLeaseManager.isEmptyPoolLeaseMapEntry(ANOTHER_LEASE_NAME))

    // should be able to acquire the other lease now
    assertTrue(requestedAnotherLease.acquire())
    assertFalse(requestedLease.acquire())
    assertFalse(poolLeaseManager.isEmptyPoolLeaseMapEntry(LEASE_NAME))
    assertFalse(poolLeaseManager.isEmptyPoolLeaseMapEntry(ANOTHER_LEASE_NAME))

    // "lose" acquisition of lease
    poolLeaseManager.clearPoolLeaseMapEntry(ANOTHER_LEASE_NAME)
    assertTrue(poolLeaseManager.isEmptyPoolLeaseMapEntry(LEASE_NAME))
    assertTrue(poolLeaseManager.isEmptyPoolLeaseMapEntry(ANOTHER_LEASE_NAME))

    // And switch back to original lease
    assertTrue(requestedLease.acquire())
    assertFalse(requestedAnotherLease.acquire())
    assertFalse(poolLeaseManager.isEmptyPoolLeaseMapEntry(LEASE_NAME))
    assertFalse(poolLeaseManager.isEmptyPoolLeaseMapEntry(ANOTHER_LEASE_NAME))

    verify(delegateLeaseManager).requestLease(LEASE_NAME)
    verify(delegateLeaseManager).requestLease(ANOTHER_LEASE_NAME)
    verify(delegateLeaseManager).requestLease(NOT_IN_POOL_LEASE_NAME)

  }

  companion object {
    private const val LEASE_NAME = "lease"
    private const val ANOTHER_LEASE_NAME = "another lease"
    private const val NOT_IN_POOL_LEASE_NAME = "not in pool lease"
    private const val POOL_NAME = "pool1"
    private val poolLeaseConfig = PoolLeaseConfig(POOL_NAME, listOf(LEASE_NAME, ANOTHER_LEASE_NAME))
  }
}

internal class TestLease(
  override val name: String,
  private val checkHeld: Boolean = true,
  private val acquire: Boolean = true,
  private val release: Boolean = true,
) : Lease {
  override fun checkHeld(): Boolean {
    return checkHeld
  }

  override fun acquire(): Boolean {
    return acquire
  }

  override fun release(): Boolean {
    return release
  }

  override fun addListener(listener: Lease.StateChangeListener) {
    TODO("Not yet implemented")
  }
}