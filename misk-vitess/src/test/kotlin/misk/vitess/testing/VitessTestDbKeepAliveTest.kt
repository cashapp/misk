package misk.vitess.testing

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import misk.docker.withMiskDefaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VitessTestDbKeepAliveTest {

  private val dockerClient: DockerClient = setupDockerClient()
  private val containerName = "keepalive_test_vitess_db"
  private val port = 33003

  @Test
  fun `database stays alive if args are the same`() {
    var vitessTestDb =
      VitessTestDb(autoApplySchemaChanges = false, containerName = containerName, port = port, keepAlive = true)

    assertDoesNotThrow(vitessTestDb::run)
    val containerId = getContainerId()

    // Now attempt to start a new instance with the same args
    vitessTestDb =
      VitessTestDb(autoApplySchemaChanges = false, containerName = containerName, port = port, keepAlive = true)

    assertDoesNotThrow(vitessTestDb::run)
    val nextContainerId = getContainerId()
    assertEquals(containerId, nextContainerId)
  }

  private fun getContainerId(): String? {
    val containers =
      dockerClient.listContainersCmd().withShowAll(true).withNameFilter(listOf("^/$containerName$")).exec()

    return containers.firstOrNull()?.id
  }

  private fun setupDockerClient(): DockerClient {
    val dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().withMiskDefaults().build()
    return DockerClientBuilder.getInstance(dockerClientConfig)
      .withDockerHttpClient(ApacheDockerHttpClient.Builder().dockerHost(dockerClientConfig.dockerHost).build())
      .build()
  }
}
