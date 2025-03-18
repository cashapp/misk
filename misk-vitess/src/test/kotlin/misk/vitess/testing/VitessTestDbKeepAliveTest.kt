package misk.vitess.testing

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import misk.docker.withMiskDefaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VitessTestDbKeepAliveTest {

  private val dockerClient: DockerClient = setupDockerClient()
  private val containerName = "keepalive_test_vitess_db"
  private val port = 33003

  @BeforeEach
  fun setup() {
    removeContainer()
  }

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

  @Test
  fun `database restarts if args change`() {
    var vitessTestDb =
      VitessTestDb(autoApplySchemaChanges = false, containerName = containerName, port = port, keepAlive = true)

    assertDoesNotThrow(vitessTestDb::run)
    val containerId = getContainerId()

    // Now attempt to start a new instance with different args
    vitessTestDb =
      VitessTestDb(
        containerName = containerName,
        port = port,
        keepAlive = true,
        vitessImage = "vitess/vttestserver:v20.0.5-mysql80",
        vitessVersion = 20,
      )

    assertDoesNotThrow(vitessTestDb::run)
    val nextContainerId = getContainerId()
    assertNotEquals(containerId, nextContainerId)
  }

  private fun getContainerId(): String? {
    val containers =
      dockerClient.listContainersCmd().withShowAll(true).withNameFilter(listOf("^/$containerName$")).exec()

    return containers.firstOrNull()?.id
  }

  private fun removeContainer() {
    // TODO: Expose a shutdown method in VitessTestDb itself to avoid needing to do this in all our tests.
    val vtgatePort = port
    val mysqlPort = port - 1
    val grpcPort = port - 2
    val basePort = port - 3
    val ports = listOf(basePort, grpcPort, mysqlPort, vtgatePort)
    ports.forEach { port ->
      val containers = dockerClient.listContainersCmd().withShowAll(true).withFilter("publish", listOf("$port")).exec()
      containers.forEach { container -> removeContainer(container) }
    }
  }

  private fun removeContainer(container: Container) {
    try {
      dockerClient.removeContainerCmd(container.id).withForce(true).exec()
    } catch (e: NotFoundException) {
      // If we are in this state, the container is already removed.
    }
  }

  private fun setupDockerClient(): DockerClient {
    val dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().withMiskDefaults().build()
    return DockerClientBuilder.getInstance(dockerClientConfig)
      .withDockerHttpClient(ApacheDockerHttpClient.Builder().dockerHost(dockerClientConfig.dockerHost).build())
      .build()
  }
}
