package misk.clustering

import com.google.common.hash.Hashing
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ClusterHashRingTest {
  @Test fun singleNode() {
    val zork = Cluster.Member("zork", "192.49.168.23")
    val hashRing =
        ClusterHashRing(members = setOf(zork), hashFn = Hashing.murmur3_32(0))
    assertThat(listOf("foo", "bar", "zed").map { hashRing[it] }).containsExactly(zork, zork, zork)
  }

  @Test fun multipleNodes() {
    val zork = Cluster.Member("zork", "192.49.168.23")
    val mork = Cluster.Member("mork", "192.49.168.24")
    val quark = Cluster.Member("quark", "192.49.168.25")

    // First version of hash ring
    val hashRing1 = ClusterHashRing(
        members = setOf(zork, mork, quark),
        hashFn = Hashing.murmur3_32(0))
    assertThat(listOf("foo", "bar", "zed", "abadidea").map {
      hashRing1[it]
    }).containsExactly(zork, quark, mork, zork)

    // Remove one of the members, only the resources mapped to that member should change
    val hashRing2 = ClusterHashRing(
        members = setOf(zork, quark),
        hashFn = Hashing.murmur3_32(0))
    assertThat(listOf("foo", "bar", "zed", "abadidea").map { hashRing2[it] })
        .containsExactly(zork, quark, zork, zork)

    // Add a new member, should not remap resources unnecessarily
    val bork = Cluster.Member("bork", "192.49.168.26")
    val hashRing3 = ClusterHashRing(
        members = setOf(zork, quark, bork),
        hashFn = Hashing.murmur3_32(0))
    assertThat(listOf("foo", "bar", "zed", "abadidea").map { hashRing3[it] })
        .containsExactly(zork, quark, zork, zork)
  }
}