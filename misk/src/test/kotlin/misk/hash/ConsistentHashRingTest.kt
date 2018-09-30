package misk.hash

import com.google.common.hash.Hashing
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

internal class ConsistentHashRingTest {
  @Test fun singleNode() {
    val hashRing = ConsistentHashRing(
        members = mapOf("zork" to "zork"),
        hashFn = Hashing.murmur3_32(0))
    Assertions.assertThat(listOf("foo", "bar", "zed").map { hashRing[it] })
        .containsExactly("zork", "zork", "zork")
  }

  @Test fun multipleNodes() {
    // First version of hash ring
    val hashRing1 = ConsistentHashRing(
        members = mapOf("zork" to "zork", "mork" to "mork", "quark" to "quark"),
        hashFn = Hashing.murmur3_32(0))
    Assertions.assertThat(listOf("foo", "bar", "zed", "abadidea").map {
      hashRing1[it]
    }).containsExactly("zork", "quark", "mork", "zork")

    // Remove one of the members, only the resources mapped to that member should change
    val hashRing2 = ConsistentHashRing(
        members = mapOf("zork" to "zork", "quark" to "quark"),
        hashFn = Hashing.murmur3_32(0))
    Assertions.assertThat(listOf("foo", "bar", "zed", "abadidea").map { hashRing2[it] })
        .containsExactly("zork", "quark", "zork", "zork")

    // Add a new member, should not remap resources unnecessarily
    val hashRing3 = ConsistentHashRing(
        members = mapOf("zork" to "zork", "quark" to "quark", "bork" to "bork"),
        hashFn = Hashing.murmur3_32(0))
    Assertions.assertThat(listOf("foo", "bar", "zed", "abadidea").map { hashRing3[it] })
        .containsExactly("zork", "quark", "zork", "zork")
  }
}