package misk.cloud.gcp.spanner

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.google.cloud.spanner.Spanner
import com.google.cloud.spanner.SpannerOptions
import com.google.inject.util.Modules
import jakarta.inject.Inject
import java.time.Duration
import misk.containers.ContainerUtil
import misk.docker.withMiskDefaults
import misk.environment.DeploymentModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import wisp.deployment.TESTING

@MiskTest(startService = false)
@Disabled("Flakey test")
class GoogleSpannerEmulatorLifecycleTest {
  val spannerConfig =
    SpannerConfig(
      project_id = "test-project",
      instance_id = "test-instance",
      database = "test-database",
      emulator =
        SpannerEmulatorConfig(enabled = true, hostname = ContainerUtil.dockerTargetOrLocalHost(), version = "1.4.9"),
    )

  @MiskTestModule val module = Modules.combine(DeploymentModule(TESTING), GoogleSpannerModule(spannerConfig))

  @Inject lateinit var emulator: GoogleSpannerEmulator

  val defaultDockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().withMiskDefaults().build()
  val httpClient =
    ApacheDockerHttpClient.Builder()
      .dockerHost(GoogleSpannerEmulator.defaultDockerClientConfig.dockerHost)
      .sslConfig(GoogleSpannerEmulator.defaultDockerClientConfig.sslConfig)
      .maxConnections(100)
      .connectionTimeout(Duration.ofSeconds(60))
      .responseTimeout(Duration.ofSeconds(120))
      .build()
  val dockerClient: DockerClient = DockerClientImpl.getInstance(defaultDockerClientConfig, httpClient)

  val spannerClient: Spanner =
    SpannerOptions.newBuilder()
      .setProjectId(spannerConfig.project_id)
      .setEmulatorHost("${spannerConfig.emulator.hostname}:${spannerConfig.emulator.port}")
      .build()
      .service

  @Nested
  inner class `#startUp` {
    @Test
    fun `starts the Docker container`() {
      // The Docker container should be running
      val isRunning: Boolean =
        dockerClient.inspectContainerCmd(GoogleSpannerEmulator.CONTAINER_NAME).exec().state.running ?: false
      assertTrue(isRunning)
    }

    @Test
    fun `can receive requests`() {
      // If it can receive requests, it will respond and not timeout or throw
      // an error.
      assertDoesNotThrow { spannerClient.instanceAdminClient.listInstances().values }
    }
  }

  @Nested
  inner class `#shutDown` {
    @Test
    fun `leaves the Docker container running`() {
      // Start emulator if it's not already running
      if (!emulator.isRunning) {
        emulator.startAsync()
        emulator.awaitRunning()
      }

      // Stop emulator
      emulator.stopAsync()
      emulator.awaitTerminated()

      // Check if emulator is running
      val isRunning: Boolean =
        dockerClient.inspectContainerCmd(GoogleSpannerEmulator.CONTAINER_NAME).exec().state.running ?: false

      // It should still be running to avoid expensive setup costs
      assertTrue(isRunning)
    }
  }

  @Nested
  inner class `#pullsImage` {
    @Test
    fun `pulls a Docker image of the emulator`() {
      var imageId: String? = null

      emulator.pullImage()

      // Throws a NotFoundError if the image isn't present locally.
      assertDoesNotThrow { imageId = dockerClient.inspectImageCmd(GoogleSpannerEmulator.IMAGE_NAME).exec().id }

      // ID of the image should be present if it's local.
      assertNotNull(imageId)
    }

    @Test
    fun `pulls a Docker image of the emulator with specified version`() {
      var imageId: String? = null

      emulator.pullImage("1.4.9")

      // Throws a NotFoundError if the image isn't present locally.
      assertDoesNotThrow { imageId = dockerClient.inspectImageCmd(GoogleSpannerEmulator.IMAGE_NAME).exec().id }

      // ID of the image should be present if it's local.
      assertNotNull(imageId)
    }
  }
}
