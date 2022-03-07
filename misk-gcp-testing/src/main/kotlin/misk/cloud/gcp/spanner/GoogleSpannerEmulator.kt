package misk.cloud.gcp.spanner

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.core.command.LogContainerResultCallback
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.michaelbull.retry.policy.binaryExponentialBackoff
import com.github.michaelbull.retry.policy.limitAttempts
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.retry
import com.google.cloud.NoCredentials
import com.google.cloud.spanner.DatabaseId
import com.google.cloud.spanner.Instance
import com.google.cloud.spanner.InstanceConfigId
import com.google.cloud.spanner.InstanceId
import com.google.cloud.spanner.InstanceInfo
import com.google.cloud.spanner.Spanner
import com.google.cloud.spanner.SpannerException
import com.google.cloud.spanner.SpannerOptions
import com.google.cloud.spanner.Statement
import com.google.common.util.concurrent.AbstractIdleService
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import wisp.containers.Composer
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSpannerEmulator @Inject constructor(
  val config: SpannerConfig,
): AbstractIdleService()  {
  private val server: SpannerServer
  private val client: Spanner

  private var containerId: String? = null
  private var containerIsRunning = false
  private var stopContainerOnExit = true
  private var startupFailure: Exception? = null

  init {
    server = SpannerServer(config = config)
    client = SpannerOptions.newBuilder()
      .setCredentials(NoCredentials.getInstance())
      .setEmulatorHost("${config.emulator.hostname}:${config.emulator.port}")
      .setProjectId(config.project_id)
      .build()
      .service

    if (shouldStartServer()) {
      // We need to do this outside of the service start up because this takes a really long time
      // the first time you do it and can cause service manager to time out.
      pullImage()
    }
  }

  private fun shouldStartServer() = config.emulator.enabled

  /**
   * Starts a Docker container running the Google Spanner emulator.
   */
  override fun startUp() {
    val startupFailure = this.startupFailure
    if (startupFailure != null) throw startupFailure
    if (containerIsRunning) return
    containerIsRunning = true

    try {
      doStart()
    } catch (e: Exception) {
      this.startupFailure = e
      throw e
    }
  }

  companion object {
    val logger = KotlinLogging.logger {}
    val defaultDockerClientConfig =
      DefaultDockerClientConfig.createDefaultConfigBuilder().build()
    val httpClient = ApacheDockerHttpClient.Builder()
      .dockerHost(defaultDockerClientConfig.dockerHost)
      .sslConfig(defaultDockerClientConfig.sslConfig)
      .maxConnections(100)
      .connectionTimeout(Duration.ofSeconds(60))
      .responseTimeout(Duration.ofSeconds(120))
      .build()
    val docker: DockerClient =
      DockerClientImpl.getInstance(defaultDockerClientConfig, httpClient)
    const val IMAGE = "gcr.io/cloud-spanner-emulator/emulator"
    const val CONTAINER_NAME = "misk-spanner-testing"

    fun pullImage() {
      if (imagePulled.get()) {
        return
      }
      synchronized(this) {
        if (imagePulled.get()) return

        val process = ProcessBuilder("bash", "-c", "docker pull $IMAGE")
          .redirectOutput(ProcessBuilder.Redirect.INHERIT)
          .redirectError(ProcessBuilder.Redirect.INHERIT)
          .start()
        process.waitFor(60, TimeUnit.MINUTES)

        if (process.exitValue() != 0) {
          throw IllegalStateException(
            "Could not pull Docker image for Spanner emulator. Make sure Docker is installed and running."
          )
        }

        imagePulled.set(true)
      }
    }

    private val imagePulled = AtomicBoolean()
  }

  /**
   * Pulls a Docker container containing the Google Spanner emulator.
   */
  fun pullImage() {
    Companion.pullImage()
  }

  private fun doStart() {
    val spannerGrpcPort = ExposedPort.tcp(server.config.emulator.port)
    val spannerHttpPort = ExposedPort.tcp(server.config.emulator.port + 10)
    val ports = Ports()
    ports.bind(spannerGrpcPort, Ports.Binding.bindPort(9010)) // 9010 is the gRPC host inside the container
    ports.bind(spannerHttpPort, Ports.Binding.bindPort(9020)) // 9020 is the HTTP host inside the container
    val containerName = CONTAINER_NAME
    val runningContainer = docker.listContainersCmd()
      .withNameFilter(listOf(containerName))
      .withLimit(1)
      .exec()
      .firstOrNull()

    if (runningContainer != null) {
      if (runningContainer.state != "running") {
        logger.info(
          "Existing Spanner named $containerName found in " +
            "state ${runningContainer.state}, force removing and restarting"
        )
        docker.removeContainerCmd(runningContainer.id).withForce(true).exec()
      } else {
        logger.info("Using existing Spanner container named $containerName")
        stopContainerOnExit = false
        containerId = runningContainer.id
      }
    }

    if (containerId == null) {
      logger.info("Starting Spanner with command")
      containerId = docker.createContainerCmd(IMAGE)
        .withExposedPorts(spannerGrpcPort)
        .withExposedPorts(spannerHttpPort)
        .withHostConfig(
          HostConfig().withPortBindings(ports)
        )
        .withTty(true)
        .withName(containerName)
        .exec().id!!
      val containerId = containerId!!
      docker.startContainerCmd(containerId).exec()
      docker.logContainerCmd(containerId)
        .withStdErr(true)
        .withStdOut(true)
        .withFollowStream(true)
        .withSince(0)
        .exec(LogContainerResultCallback())
        .awaitStarted()
    }
    logger.info("Started Spanner with container id $containerId")

    waitUntilHealthy()
    createDatabase()
  }

  private fun waitUntilHealthy() {
    try {
      runBlocking {
        retry(
          limitAttempts(20) +
            binaryExponentialBackoff(1L, 5L)
        ) {
          // The query will fail if the server is not responding
          client.instanceAdminClient.listInstances().values
        }
      }
    } catch (e: Exception) {
      throw Exception("Spanner server failed to start up in time", e)
    }
  }

  private fun createDatabase() {
    val instanceId = "misk-test-instance"
    var instance: Instance

    try {
      instance = client.instanceAdminClient.getInstance(config.instance_id)
    } catch (e: SpannerException) {
      instance = client.instanceAdminClient.createInstance(
        InstanceInfo.newBuilder(
          InstanceId.of(config.project_id, config.instance_id),
        ).setInstanceConfigId(
          InstanceConfigId.of(config.project_id, "emulator-config")
        ).build()
      ).get()
    }

    try {
      instance.getDatabase(config.database)
    } catch (e: SpannerException) {
      instance.createDatabase(config.database, listOf())
    }
  }

  /**
   * Stops a Docker container running the Google Spanner emulator.
   */
  override fun shutDown() {
    client.close()
    logger.info(
      "Leaving Spanner docker container running in the background. " +
        "If you need to kill it because you messed up migrations or something use:" +
        "\n\tdocker kill $CONTAINER_NAME"
    )
  }

  fun clearTables() {
    val dataClient = client.getDatabaseClient(
      DatabaseId.of(config.project_id, config.instance_id, config.database)
    )
    val tableNameQuery = dataClient.singleUseReadOnlyTransaction().executeQuery(
      Statement.of(
        """
        SELECT
          table_name
        FROM
          information_schema.tables
        WHERE
          table_catalog = '' and table_schema = ''
        """.trimIndent()
      )
    )
    val tableNames: MutableList<String> = mutableListOf()
    while (tableNameQuery.next()) {
      tableNames.add(tableNameQuery.getString(0))
    }
    if (tableNames.size == 0) return
    dataClient.readWriteTransaction().run {
      it.batchUpdate(
        tableNames.map {
          tableName -> Statement.of("DELETE FROM ${tableName} WHERE true")
        }
      )
    }
  }

  private class SpannerServer(
    val config: SpannerConfig
  ) {
  }
}
