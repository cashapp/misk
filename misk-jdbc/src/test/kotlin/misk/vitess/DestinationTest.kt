package misk.vitess

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class DestinationTest {
  @Test
  fun parse() {
    Assertions.assertThat(Destination.parse("").isBlank()).isTrue()
    Assertions.assertThat(Destination.parse("@master").tabletType)
        .isEqualTo(TabletType.MASTER)
    Assertions.assertThat(Destination.parse("ks/-80").shard)
        .isEqualTo(Shard(Keyspace("ks"), "-80"))
    Assertions.assertThat(Destination.parse("ks/-80@replica").tabletType)
        .isEqualTo(TabletType.REPLICA)
  }

  @Test
  fun testToString() {
    Assertions.assertThat(Destination(null, null, null).toString()).isEqualTo("")
    Assertions.assertThat(Destination(Shard(Keyspace("ks"), "-80")).toString()).isEqualTo("ks/-80")
    Assertions.assertThat(Destination(Shard(Keyspace("ks"), "-80"), TabletType.REPLICA).toString()).isEqualTo("ks/-80@replica")
    Assertions.assertThat(Destination(Keyspace("ks"), null, TabletType.REPLICA).toString()).isEqualTo("ks@replica")
  }
}