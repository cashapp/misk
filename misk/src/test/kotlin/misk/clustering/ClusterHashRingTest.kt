package misk.clustering

import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Multimaps
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

  @Test fun multipleNodes2() {
    // pod names
    val pods = setOf(
        Cluster.Member("newswriter-6c4d9d49-mvm75", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-sfs6g", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-nmp2x", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-m6gpq", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-wbk94", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-sbkbn", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-f6m2l", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-9nxd9", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-kn82g", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-b87xd", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-nntwg", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-8lk28", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-dshd4", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-556np", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-xc67k", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-hhjxd", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-q2cs4", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-6pc76", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-tmqdx", "192.49.168.23"),
        Cluster.Member("newswriter-6c4d9d49-jwswq", "192.49.168.23")
    )
    // resource names
    val resources = listOf(
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022029",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022019",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022012",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac02201b",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac02201d",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac02201a",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022028",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022027",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022024",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022026",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022023",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022020",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022025",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022022",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022021",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac02202d",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac02202f",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac02202c",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac02202e",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac02202b",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac02202a",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022017",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022016",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022013",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022018",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022015",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022031",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022014",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac022030",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac02201f",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac02201c",
        "evently-consumer-legacy_transaction_events_2-08808004100218d6daf5ac02201e"
    )
    val hashRing = ClusterHashRing(
        members = pods,
        hashFn = Hashing.murmur3_32())
    val mappings = resources.map {
      hashRing[it] to it
    }
    val assignments = LinkedHashMultimap.create<Cluster.Member, String>()
    for ((member, resource) in mappings) {
      assignments.put(member, resource)
    }
    print(assignments)
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
        hashFn = FakeHashFn(mapOf(
          "a 0" to 100,
          "b 0" to 200,
          "c 0" to 300,
          "foo" to 50,
          "bar" to 150,
          "zed" to 250,
          "zork" to 350
        )),
        vnodesCount = 1)
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