package misk.vitess

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DestinationTest {
  @Test
  fun parsePrimaryDestination() {
    val primaryDestination = Destination.parse("@primary")
    assertThat(primaryDestination.tabletType).isEqualTo(TabletType.PRIMARY)
    assertThat(primaryDestination.keyspace).isNull()
    assertThat(primaryDestination.shard).isNull()

    val backwardsCompatibleDestination = Destination.parse("@master")
    assertThat(backwardsCompatibleDestination.tabletType).isEqualTo(TabletType.PRIMARY)
    assertThat(backwardsCompatibleDestination.keyspace).isNull()
    assertThat(backwardsCompatibleDestination.shard).isNull()
  }

  @Test
  fun parseReplicaDestination() {
    val destination = Destination.parse("@replica")
    assertThat(destination.tabletType).isEqualTo(TabletType.REPLICA)
    assertThat(destination.keyspace).isNull()
    assertThat(destination.shard).isNull()
  }

  @Test
  fun parseKeyspaceWithShardDestination() {
    val destination = Destination.parse("ks/-80")
    assertThat(destination.tabletType).isNull()
    assertThat(destination.keyspace).isEqualTo(Keyspace("ks"))
    assertThat(destination.shard).isEqualTo(Shard(Keyspace("ks"), "-80"))
  }

  @Test
  fun parseKeyspaceWithShardDestinationAndQualifier() {
    val destination = Destination.parse("ks/-80@replica")
    assertThat(destination.tabletType).isEqualTo(TabletType.REPLICA)
    assertThat(destination.keyspace).isEqualTo(Keyspace("ks"))
    assertThat(destination.shard).isEqualTo(Shard(Keyspace("ks"), "-80"))
  }

  @Test
  fun parseOnlyKeyspace() {
    val destination = Destination.parse("ks")
    assertThat(destination.tabletType).isNull()
    assertThat(destination.keyspace).isEqualTo(Keyspace("ks"))
    assertThat(destination.shard).isNull()
  }

  @Test
  fun parseBlankCatalogString() {
    val blankDestination = Destination.parse("")
    assertThat(blankDestination.isBlank()).isTrue()
    assertThat(blankDestination.shard).isNull()
    assertThat(blankDestination.tabletType).isNull()
    assertThat(blankDestination.keyspace).isNull()
  }

  @Test
  fun parseInvalidDestinationQualifier() {
    val exception = Assertions.catchThrowable {
      Destination.parse("@invalid")
    }
    assertThat(exception)
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Invalid destination qualifier")
  }

  @Test
  fun primary() {
    val primaryDestination = Destination.primary()
    assertThat(primaryDestination.tabletType).isEqualTo(TabletType.PRIMARY)
    assertThat(primaryDestination.keyspace).isNull()
    assertThat(primaryDestination.shard).isNull()
    assertThat("$primaryDestination").isEqualTo("@primary")
  }

  @Test
  fun replica() {
    val replicaDestination = Destination.replica()
    assertThat(replicaDestination.tabletType).isEqualTo(TabletType.REPLICA)
    assertThat(replicaDestination.keyspace).isNull()
    assertThat(replicaDestination.shard).isNull()
    assertThat("$replicaDestination").isEqualTo("@replica")
  }

  @Test
  fun testToString() {
    assertThat("${Destination(null, null, null)}").isEqualTo("")
    assertThat("${Destination(null, null, TabletType.PRIMARY)}").isEqualTo("@primary")
    assertThat("${Destination(null, null, TabletType.REPLICA)}").isEqualTo("@replica")
    assertThat("${Destination(Shard(Keyspace("ks"), "-80"))}").isEqualTo("ks/-80")
    assertThat("${Destination(Shard(Keyspace("ks"), "-80"), TabletType.REPLICA)}").isEqualTo("ks/-80@replica")
    assertThat("${Destination(Keyspace("ks"), null, TabletType.REPLICA)}").isEqualTo("ks@replica")
    assertThat("${Destination(Keyspace("ks"), null, null)}").isEqualTo("ks")
  }
}
