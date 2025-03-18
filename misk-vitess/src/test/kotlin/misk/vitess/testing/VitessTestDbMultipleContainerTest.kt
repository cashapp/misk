package misk.vitess.testing

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.Future
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VitessTestDbMultipleContainerTest {

  private val containerName1 = "vitess_test_db_1"
  private val containerPort1 = 28003
  private val containerName2 = "vitess_test_db_2"
  private val containerPort2 = 29003
  private val executorService = Executors.newFixedThreadPool(2)

  @AfterAll
  fun teardown() {
    removeContainers()
    executorService.shutdown()
  }

  @Test
  fun `multiple containers can run`() {
    var vitessTestDb1 = VitessTestDb(containerName = containerName1, keepAlive = false, port = containerPort1)

    var vitessTestDb2 = VitessTestDb(containerName = containerName2, keepAlive = false, port = containerPort2)

    val future1: Future<Unit> = executorService.submit<Unit> { assertDoesNotThrow(vitessTestDb1::run) }
    val future2: Future<Unit> = executorService.submit<Unit> { assertDoesNotThrow(vitessTestDb2::run) }

    future1.get()
    future2.get()

    // Check the health of both containers
    assertTrue(isContainerHealthy(containerName1))
    assertTrue(isContainerHealthy(containerName2))
  }

  fun isContainerHealthy(containerName: String): Boolean {
    val processBuilder = ProcessBuilder("docker", "inspect", "--format", "{{.State.Health.Status}}", containerName)
    val process = processBuilder.start()
    val reader = BufferedReader(InputStreamReader(process.inputStream))

    val healthStatus = reader.readLine()
    return healthStatus == "healthy"
  }

  fun removeContainers(): String? {
    val processBuilder = ProcessBuilder("docker", "rm", "-fv", containerName1, containerName2)
    val process = processBuilder.start()
    val reader = BufferedReader(InputStreamReader(process.inputStream))

    return reader.readLine()
  }
}
