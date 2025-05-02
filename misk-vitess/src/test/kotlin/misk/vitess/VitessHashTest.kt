package misk.vitess

import okio.ByteString.Companion.decodeHex
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VitessHashTest {
  @Test
  fun hashesLikeVitess() {
    assertThat(VitessHash.toKeyspaceId(1)).isEqualTo("166b40b44aba4bd6".decodeHex())
    assertThat(VitessHash.toKeyspaceId(2)).isEqualTo("06e7ea22ce92708f".decodeHex())
    assertThat(VitessHash.toKeyspaceId(3)).isEqualTo("4eb190c9a2fa169c".decodeHex())
    assertThat(VitessHash.toKeyspaceId(4)).isEqualTo("d2fd8867d50d2dfe".decodeHex())
    assertThat(VitessHash.toKeyspaceId(5)).isEqualTo("70bb023c810ca87a".decodeHex())
    assertThat(VitessHash.toKeyspaceId(6)).isEqualTo("f098480ac4c4be71".decodeHex())
  }

  @Test
  fun unhashesLikeVitess() {
    assertThat(VitessHash.fromKeyspaceId("166b40b44aba4bd6".decodeHex())).isEqualTo(1)
    assertThat(VitessHash.fromKeyspaceId("06e7ea22ce92708f".decodeHex())).isEqualTo(2)
    assertThat(VitessHash.fromKeyspaceId("4eb190c9a2fa169c".decodeHex())).isEqualTo(3)
    assertThat(VitessHash.fromKeyspaceId("d2fd8867d50d2dfe".decodeHex())).isEqualTo(4)
    assertThat(VitessHash.fromKeyspaceId("70bb023c810ca87a".decodeHex())).isEqualTo(5)
    assertThat(VitessHash.fromKeyspaceId("f098480ac4c4be71".decodeHex())).isEqualTo(6)
  }

  @Test
  fun shardRange() {
    // 80-90
    assertThat("81abcdef".decodeHex()).isGreaterThan("80".decodeHex())
    assertThat("81abcdef".decodeHex()).isLessThan("90".decodeHex())

    // 8080-90
    assertThat("81abcdef".decodeHex()).isGreaterThan("8080".decodeHex())
    assertThat("81abcdef".decodeHex()).isLessThan("90".decodeHex())
  }
}
