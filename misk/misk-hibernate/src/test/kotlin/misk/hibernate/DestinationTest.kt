package misk.hibernate

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DestinationTest {
  @Test
  fun parse() {
    assertThat(Destination.parse("").isBlank()).isTrue()
    assertThat(Destination.parse("@master").tabletType)
        .isEqualTo(TabletType.MASTER)
    assertThat(Destination.parse("ks/-80").shard)
        .isEqualTo(Shard(Keyspace("ks"), "-80"))
    assertThat(Destination.parse("ks/-80@replica").tabletType)
        .isEqualTo(TabletType.REPLICA)
  }

  @Test
  fun testToString() {
    assertThat(Destination(null, null, null).toString()).isEqualTo("")
    assertThat(Destination(Shard(Keyspace("ks"), "-80")).toString()).isEqualTo("ks/-80")
    assertThat(Destination(Shard(Keyspace("ks"), "-80"), TabletType.REPLICA).toString()).isEqualTo("ks/-80@replica")
    assertThat(Destination(Keyspace("ks"), null, TabletType.REPLICA).toString()).isEqualTo("ks@replica")
  }
}
