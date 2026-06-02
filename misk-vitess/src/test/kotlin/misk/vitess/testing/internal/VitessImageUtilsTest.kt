package misk.vitess.testing.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VitessImageUtilsTest {

  @Test
  fun `derives vtctldclient from ghcr block image`() {
    assertEquals(
      "ghcr.io/block/vitess/vtctldclient:23.0.3-block.1",
      deriveVtctldClientImage("ghcr.io/block/vitess/vttestserver:23.0.3-block.1-mysql84"),
    )
  }

  @Test
  fun `derives vtctldclient from upstream vitess image`() {
    assertEquals("vitess/vtctldclient:v21.0.4", deriveVtctldClientImage("vitess/vttestserver:v21.0.4-mysql80"))
  }

  @Test
  fun `derives vtctldclient from custom registry image`() {
    assertEquals(
      "my-registry.example.com/vitess/vtctldclient:23.0.3-block.1",
      deriveVtctldClientImage("my-registry.example.com/vitess/vttestserver:23.0.3-block.1-mysql84"),
    )
  }

  @Test
  fun `derives vtctldclient with mysql80 suffix`() {
    assertEquals("vitess/vtctldclient:v22.0.2", deriveVtctldClientImage("vitess/vttestserver:v22.0.2-mysql80"))
  }

  @Test
  fun `derives vtctldclient without mysql suffix is unchanged`() {
    assertEquals("vitess/vtctldclient:v23.0.3", deriveVtctldClientImage("vitess/vttestserver:v23.0.3"))
  }

  @Test
  fun `derives vtctldclient from docker hub image`() {
    assertEquals(
      "docker.io/vitess/vtctldclient:v23.0.3",
      deriveVtctldClientImage("docker.io/vitess/vttestserver:v23.0.3-mysql84"),
    )
  }
}
