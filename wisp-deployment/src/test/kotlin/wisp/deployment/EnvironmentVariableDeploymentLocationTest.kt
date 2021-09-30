package wisp.deployment

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class EnvironmentVariableDeploymentLocationTest {

  private val envVar = "HOST"
  private val envVarValue = "my-host"

  @Test
  fun `deployment location set from environment variable`() {
    val environmentVariableLoader = FakeEnvironmentVariableLoader(
      mutableMapOf(
        envVar to envVarValue
      )
    )

    assertEquals(
      envVarValue,
      EnvironmentVariableDeploymentLocation(envVar, environmentVariableLoader).id
    )
  }
}
