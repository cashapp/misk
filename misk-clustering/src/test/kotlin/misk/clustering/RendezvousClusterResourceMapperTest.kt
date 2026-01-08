package misk.clustering

import kotlin.math.floor
import kotlin.math.log2
import org.apache.commons.math3.stat.inference.ChiSquareTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows

internal class RendezvousClusterResourceMapperTest {
  @Test
  fun `throws an error when there are zero members`() {
    val hashRing = RendezvousClusterResourceMapper(members = setOf())
    assertThrows<NoMembersAvailableException> { hashRing["foo"] }
  }

  @Test
  fun `can hash with a single member`() {
    val zork = Cluster.Member("zork", "192.49.168.23")
    val hashRing = RendezvousClusterResourceMapper(members = setOf(zork))
    assertThat(listOf("foo", "bar", "zed").map { hashRing[it] }).containsExactly(zork, zork, zork)
  }

  @Test
  fun `hash is consistent when nodes are removed`() {
    val leaseCount = 1_024
    val leasePrefix = "test-resource:"
    val zork = Cluster.Member("zork", "192.49.168.23")
    val mork = Cluster.Member("mork", "192.49.168.24")
    val quark = Cluster.Member("quark", "192.49.168.25")

    // Generate assignments for 3 members
    val assignments: MutableMap<Cluster.Member, MutableSet<String>> = mutableMapOf()
    assignments[zork] = mutableSetOf()
    assignments[mork] = mutableSetOf()
    assignments[quark] = mutableSetOf()
    val mapper = RendezvousClusterResourceMapper(setOf(zork, mork, quark))
    for (i in 0..<leaseCount) {
      val lease = "${leasePrefix}${i}"
      val assignee = mapper[lease]
      assignments[assignee]?.add(lease)
    }

    // Generate assignments for 2 members
    val newAssignments: MutableMap<Cluster.Member, MutableSet<String>> = mutableMapOf()
    newAssignments[zork] = mutableSetOf()
    newAssignments[mork] = mutableSetOf()
    val newMapper = RendezvousClusterResourceMapper(setOf(zork, mork))
    for (i in 0..<leaseCount) {
      val lease = "${leasePrefix}${i}"
      val assignee = newMapper[lease]
      newAssignments[assignee]?.add(lease)
    }

    // Check that existing mappings are maintained
    assertTrue(newAssignments[zork]!!.containsAll(assignments[zork]!!))
    assertTrue(newAssignments[mork]!!.containsAll(assignments[mork]!!))
  }

  @Test
  fun `evaluations are cached`() {
    val zork = Cluster.Member("zork", "192.49.168.23")
    val mork = Cluster.Member("mork", "192.49.168.24")
    val quark = Cluster.Member("quark", "192.49.168.25")
    val resourceId = "test-resource"
    val mapper = RendezvousClusterResourceMapper(setOf(zork, mork, quark))

    assertNull(mapper.getFromCache(resourceId))

    val member = mapper[resourceId]

    assertEquals(member, mapper.getFromCache(resourceId))
  }

  @Test
  fun `hashes are uniformly distributed`() {
    val member = Cluster.Member("zork", "192.49.168.23")
    val leasePrefix = "test-lease:"
    val leaseCount = 1_024
    val bucketCount = 128
    val leasesPerBucket = leaseCount.toDouble() / bucketCount.toDouble()
    val shift = 64 - floor(log2(bucketCount.toDouble())).toInt()

    val expectedData = DoubleArray(bucketCount) { _ -> leasesPerBucket }
    val observedData = LongArray(bucketCount)
    val mapper = RendezvousClusterResourceMapper(setOf(member))
    for (i in 0..<leaseCount) {
      val result = mapper.hashClusterResource(member, "${leasePrefix}${i}")
      observedData[(result ushr shift).toInt()]++
    }

    // Check if we reject the null hypothesis
    assertFalse(ChiSquareTest().chiSquareTest(expectedData, observedData, 0.05))
  }

  @Test
  fun `assignments are uniformly distributed`() {
    val leaseCount = 1_024
    val memberCount = 128
    val leasesPerMember = leaseCount.toDouble() / memberCount.toDouble()
    val members: MutableSet<Cluster.Member> = mutableSetOf()
    for (i in 0..<memberCount) {
      members.add(Cluster.Member(i.toString(), "192.49.168.${i}"))
    }

    val expectedData = DoubleArray(memberCount) { _ -> leasesPerMember }
    val observedData = LongArray(memberCount)
    val mapper = RendezvousClusterResourceMapper(members)
    for (i in 0..<leaseCount) {
      val result = mapper["test-lease:${i}"]
      observedData[result.name.toInt()]++
    }

    // Check if we reject the null hypothesis
    assertFalse(ChiSquareTest().chiSquareTest(expectedData, observedData, 0.05))
  }
}
