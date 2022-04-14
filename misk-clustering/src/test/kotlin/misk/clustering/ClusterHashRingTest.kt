package misk.clustering

import com.google.common.hash.HashCode
import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
      hashFn = Hashing.murmur3_32(0)
    )
    assertThat(
      listOf("foo", "bar", "zed", "abadidea").map {
        hashRing1[it]
      }
    ).containsExactly(zork, quark, mork, zork)

    // Remove one of the members, only the resources mapped to that member should change
    val hashRing2 = ClusterHashRing(
      members = setOf(zork, quark),
      hashFn = Hashing.murmur3_32(0)
    )
    assertThat(listOf("foo", "bar", "zed", "abadidea").map { hashRing2[it] })
      .containsExactly(zork, quark, zork, zork)

    // Add a new member, should not remap resources unnecessarily
    val bork = Cluster.Member("bork", "192.49.168.26")
    val hashRing3 = ClusterHashRing(
      members = setOf(zork, quark, bork),
      hashFn = Hashing.murmur3_32(0)
    )
    assertThat(listOf("foo", "bar", "zed", "abadidea").map { hashRing3[it] })
      .containsExactly(zork, quark, zork, zork)
  }

  @Test fun zeroNodes() {
    val hashRing =
      ClusterHashRing(members = setOf(), hashFn = Hashing.murmur3_32(0))
    assertThrows<NoMembersAvailableException> {
      hashRing["foo"]
    }
  }

  @Test fun resourceToRangeMapping() {
    /*
     If we have 3 vnodes and they hash to a, b, and c, we want to map the hashed resource ID to
     vnodes using the following ranges.
      [0, a] => a
      (a, b] => b
      (b, c] => c
      (c, INT_MAX] => a
     This test ensures that each range ends up mapping to the expected vnode.
     */
    val a = Cluster.Member("a", "192.49.168.23")
    val b = Cluster.Member("b", "192.49.168.24")
    val c = Cluster.Member("c", "192.49.168.25")

    // First version of hash ring
    val hashRing = ClusterHashRing(
      members = setOf(a, b, c),
      hashFn = FakeHashFn(
        mapOf(
          "a 0" to 100,
          "b 0" to 200,
          "c 0" to 300,
          "foo" to 50,
          "bar" to 150,
          "zed" to 250,
          "zork" to 350
        )
      ),
      vnodesCount = 1
    )
    assertThat(listOf("foo", "bar", "zed", "zork").map { hashRing[it] })
      .containsExactly(a, b, c, a)
  }

  /**
   * Hash function that uses pre-determined mapping to determine hashes for inputs.
   *
   * Does not actually delegate to sha256. It just uses delegation to avoid implementing the full
   * interface.
   */
  class FakeHashFn(val hashes: Map<String, Int>) : HashFunction by Hashing.sha256() {
    override fun hashBytes(input: ByteArray): HashCode {
      return HashCode.fromInt(hashes.getValue(String(input)).toInt())
    }
  }
}
