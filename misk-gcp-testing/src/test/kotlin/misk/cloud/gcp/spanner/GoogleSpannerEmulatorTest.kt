package misk.cloud.gcp.spanner

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.google.cloud.spanner.DatabaseId
import com.google.cloud.spanner.Key
import com.google.cloud.spanner.KeySet
import com.google.cloud.spanner.Mutation
import com.google.cloud.spanner.Spanner
import com.google.cloud.spanner.SpannerOptions
import com.google.inject.util.Modules
import misk.environment.DeploymentModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import wisp.containers.ContainerUtil
import wisp.deployment.TESTING
import java.time.Duration
import javax.inject.Inject

@MiskTest
class GoogleSpannerEmulatorTest {
  val spannerConfig = SpannerConfig(
    project_id = "test-project",
    instance_id = "test-instance",
    database = "test-database",
    emulator = SpannerEmulatorConfig(
      enabled = true,
      hostname = ContainerUtil.dockerTargetOrLocalHost()
    )
  )

  @MiskTestModule
  val module = Modules.combine(
    DeploymentModule(TESTING),
    GoogleSpannerModule(spannerConfig),
  )

  @Inject lateinit var emulator: GoogleSpannerEmulator

  val defaultDockerClientConfig =
    DefaultDockerClientConfig.createDefaultConfigBuilder().build()
  val httpClient = ApacheDockerHttpClient.Builder()
    .dockerHost(GoogleSpannerEmulator.defaultDockerClientConfig.dockerHost)
    .sslConfig(GoogleSpannerEmulator.defaultDockerClientConfig.sslConfig)
    .maxConnections(100)
    .connectionTimeout(Duration.ofSeconds(60))
    .responseTimeout(Duration.ofSeconds(120))
    .build()
  val dockerClient: DockerClient =
    DockerClientImpl.getInstance(defaultDockerClientConfig, httpClient)

  val spannerClient: Spanner = SpannerOptions.newBuilder()
    .setProjectId(spannerConfig.project_id)
    .setEmulatorHost(
      "${spannerConfig.emulator.hostname}:${spannerConfig.emulator.port}"
    )
    .build()
    .service

  @Nested
  inner class `#startUp` {
    @BeforeEach
    fun startEmulator() {
      if (!emulator.isRunning) {
        emulator.startAsync()
        emulator.awaitRunning()
      }
    }

    @AfterEach
    fun stopEmulator() {
      if (emulator.isRunning) {
        emulator.stopAsync()
        emulator.awaitTerminated()
      }
    }

    @Test fun `starts the Docker container`() {
      // The Docker container should be running
      val isRunning: Boolean = dockerClient.inspectContainerCmd(
        GoogleSpannerEmulator.CONTAINER_NAME
      ).exec().state.running ?: false
      assertTrue(isRunning)
    }

    @Test fun `can receive requests`() {
      // If it can receive requests, it will respond and not timeout or throw
      // an error.
      assertDoesNotThrow {
        spannerClient.instanceAdminClient.listInstances().values
      }
    }
  }

  @Nested
  inner class `#shutDown` {
    @Test fun `leaves the Docker container running`() {
      // Start emulator if it's not already running
      if (!emulator.isRunning) {
        emulator.startAsync()
        emulator.awaitRunning()
      }

      // Stop emulator
      emulator.stopAsync()
      emulator.awaitTerminated()

      // Check if emulator is running
      val isRunning: Boolean = dockerClient.inspectContainerCmd(
        GoogleSpannerEmulator.CONTAINER_NAME
      ).exec().state.running ?: false

      // It should still be running to avoid expensive setup costs
      assertTrue(isRunning)
    }
  }

  @Nested
  inner class `#pullsImage` {
    @Test fun `pulls a Docker image of the emulator`() {
      var imageId: String? = null

      emulator.pullImage()

      // Throws a NotFoundError if the image isn't present locally.
      assertDoesNotThrow {
        imageId = dockerClient.inspectImageCmd(
          GoogleSpannerEmulator.IMAGE
        ).exec().id
      }

      // ID of the image should be present if it's local.
      assertNotNull(imageId)
    }
  }

  @Nested
  inner class `#clearTables` {
    @Test fun `truncates all tables`() {
      // The emulator will create the database and instance for us using the
      // config, so we don't need to worry about checking for their existence.
      val adminClient = spannerClient.instanceAdminClient
        .getInstance(spannerConfig.instance_id)
        .getDatabase(spannerConfig.database)
      val dataClient = spannerClient.getDatabaseClient(
        DatabaseId.of(
          spannerConfig.project_id,
          spannerConfig.instance_id,
          spannerConfig.database,
        )
      )
      val personId = "abc123"
      fun personExists(): Boolean {
        val query = dataClient.singleUseReadOnlyTransaction().read(
          "people",
          KeySet.singleKey(
            Key.of(personId)
          ),
          listOf("id")
        )

        // Load results
        if (query.next()) {
          return personId == query.getString(0)
        } else {
          return false
        }
      }

      // Check if there's a people table.
      val hasPeopleTable = adminClient.ddl.any { it.contains("CREATE TABLE people") }

      // If it doesn't exist, create it
      if (!hasPeopleTable) {
        // Create a "people" table
        adminClient.updateDdl(
          listOf(
            """
            CREATE TABLE people (id STRING(6)) PRIMARY KEY (id)
            """.trimIndent()
          ),
          null
        ).get()
      }

      // Insert a person
      dataClient.write(
        listOf(
          Mutation.newInsertBuilder("people")
            .set("id").to(personId)
            .build()
        )
      )

      // Expect that the person exists in the DB
      assertTrue(personExists())

      // Clear the tables
      emulator.clearTables()

      // Expect that no person exists
      assertFalse(personExists())
    }
  }
}
