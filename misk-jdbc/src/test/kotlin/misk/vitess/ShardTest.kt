package misk.vitess

import misk.vitess.Keyspace
import misk.vitess.Shard
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ShardIdTest {

  private val keyspace = Keyspace("myKeyspace")

  @Test fun unsharded() {
    val shardId = Shard(keyspace, "0")
    val keyRange = shardId.keyRange()
    assertThat(keyRange.hasLowerBound()).isFalse()
    assertThat(keyRange.hasUpperBound()).isFalse()
  }

  @Test fun unbounded() {
    val shardId = Shard(keyspace, "-")
    val keyRange = shardId.keyRange()
    assertThat(keyRange.hasLowerBound()).isFalse()
    assertThat(keyRange.hasUpperBound()).isFalse()
  }

  @Test fun noLowerBound() {
    val shardId = Shard(keyspace, "-80")
    val keyRange = shardId.keyRange()
    assertThat(keyRange.hasLowerBound()).isFalse()
    assertThat(keyRange.upperEndpoint()).isEqualTo(Shard.Key("80"))
    assertThat(keyRange.contains(Shard.Key("80"))).isFalse()
  }

  @Test fun noUpperBound() {
    val shardId = Shard(keyspace, "80-")
    val keyRange = shardId.keyRange()
    assertThat(keyRange.hasUpperBound()).isFalse()
    assertThat(keyRange.lowerEndpoint()).isEqualTo(Shard.Key("80"))
    assertThat(keyRange.contains(Shard.Key("80"))).isTrue()
  }

  @Test fun bounded() {
    val shardId = Shard(keyspace, "80-90")
    val keyRange = shardId.keyRange()
    assertThat(keyRange.lowerEndpoint()).isEqualTo(Shard.Key("80"))
    assertThat(keyRange.upperEndpoint()).isEqualTo(Shard.Key("90"))
    assertThat(keyRange.contains(Shard.Key("80"))).isTrue()
    assertThat(keyRange.contains(Shard.Key("8fff"))).isTrue()
    assertThat(keyRange.contains(Shard.Key("90"))).isFalse()
  }

  @Test fun boundedLong() {
    val shardId = Shard(keyspace, "80f0-90f0")
    val keyRange = shardId.keyRange()
    assertThat(keyRange.contains(Shard.Key("90"))).isTrue()
  }
}
