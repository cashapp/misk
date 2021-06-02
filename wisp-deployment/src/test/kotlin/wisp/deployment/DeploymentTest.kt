package wisp.deployment

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class DeploymentTest {
  private lateinit var environmentVariableLoader: FakeEnvironmentVariableLoader

  @BeforeEach
  internal fun setUp() {
    environmentVariableLoader = FakeEnvironmentVariableLoader()
  }

  @Test
  fun defaultDeploymentEnvIsDevelopment() {
    val deployment = getDeploymentFromEnvironmentVariable(
      environmentVariableLoader = environmentVariableLoader
    )
    assertTrue(deployment.isLocalDevelopment)
    assertFalse(deployment.isTest)
    assertFalse(deployment.isProduction)
    assertFalse(deployment.isStaging)
  }

  @Test
  fun deploymentEnvIsSetProperly() {
    val props = listOf(
      Deployment::isProduction,
      Deployment::isStaging,
      Deployment::isTest,
      Deployment::isLocalDevelopment
    )
    val environmentMap = mapOf(
      "production" to Deployment::isProduction,
      "staging" to Deployment::isStaging,
      "testing" to Deployment::isTest,
      "test" to Deployment::isTest,
      "development" to Deployment::isLocalDevelopment
    )
    for ((envVar, envProperty) in environmentMap) {
      environmentVariableLoader = FakeEnvironmentVariableLoader(mutableMapOf("ENVIRONMENT" to envVar))
      val deployment = getDeploymentFromEnvironmentVariable(
        environmentVariableLoader = environmentVariableLoader
      )
      assertTrue(envProperty.invoke(deployment))
      props
        .filter { it != envProperty }
        .forEach { assertFalse(it.invoke(deployment)) }
    }
  }
}
