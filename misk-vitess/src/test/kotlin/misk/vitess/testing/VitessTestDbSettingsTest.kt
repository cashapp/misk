package misk.vitess.testing

import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VitessTestDbSettingsTest {
  @Test
  fun `vtctldclient timeout defaults to ten seconds`() {
    assertEquals(
      Duration.ofSeconds(10),
      vtctldClientTimeoutFromEnvironment(emptyMap()),
    )
  }

  @Test
  fun `vtctldclient timeout uses container wait environment value`() {
    assertEquals(
      Duration.ofSeconds(30),
      vtctldClientTimeoutFromEnvironment(mapOf("VTCTLDCLIENT_CONTAINER_START_DELAY_MS" to "30000")),
    )
  }

  @Test
  fun `vtctldclient timeout uses apply vschema environment value`() {
    assertEquals(
      Duration.ofSeconds(30),
      vtctldClientTimeoutFromEnvironment(mapOf("VTCTLDCLIENT_APPLY_VSCHEMA_TIMEOUT_MS" to "30000ms")),
    )
  }

  @Test
  fun `vtctldclient timeout uses larger value when both environment values are present`() {
    assertEquals(
      Duration.ofSeconds(45),
      vtctldClientTimeoutFromEnvironment(
        mapOf(
          "VTCTLDCLIENT_CONTAINER_START_DELAY_MS" to "30000",
          "VTCTLDCLIENT_APPLY_VSCHEMA_TIMEOUT_MS" to "45000ms",
        )
      ),
    )
  }
}
