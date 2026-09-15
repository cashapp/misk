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
import com.google.cloud.spanner.ErrorCode
import com.google.cloud.spanner.Instance
import com.google.cloud.spanner.InstanceConfigId
import com.google.cloud.spanner.InstanceId
import com.google.cloud.spanner.InstanceInfo
import com.google.cloud.spanner.Spanner
import com.google.cloud.spanner.SpannerException
import com.google.cloud.spanner.SpannerOptions
import com.google.cloud.spanner.Statement
import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import misk.docker.withMiskDefaults
import misk.testing.TestFixture
import mu.KotlinLogging

@Singleton
class GoogleSpannerEmulator @Inject constructor(val config: SpannerConfig) : AbstractIdleService(), TestFixture {
  private val server: SpannerServer
  private val client: Spanner

  private var containerId: String? = null
  private var containerIsRunning = false
  private var stopContainerOnExit = true
  private var startupFailure: Exception? = null

  init {
    server = SpannerServer(config = config)
    client =
      SpannerOptions.newBuilder()
        .setCredentials(NoCredentials.getInstance())
        .setEmulatorHost("${config.emulator.hostname}:${config.emulator.port}")
        .setProjectId(config.project_id)
        .build()
        .service

    if (shouldStartServer()) {
      // We need to do this outside of the service start up because this takes a really long time
      // the first time you do it and can cause service manager to time out.
      pullImage(config.emulator.version)
    }
  }

  private fun shouldStartServer() = config.emulator.enabled

  /** Starts a Docker container running the Google Spanner emulator. */
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
    val defaultDockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().withMiskDefaults().build()
    val httpClient =
      ApacheDockerHttpClient.Builder()
        .dockerHost(defaultDockerClientConfig.dockerHost)
        .sslConfig(defaultDockerClientConfig.sslConfig)
        .maxConnections(100)
        .connectionTimeout(Duration.ofSeconds(60))
        .responseTimeout(Duration.ofSeconds(120))
        .build()
    val docker: DockerClient = DockerClientImpl.getInstance(defaultDockerClientConfig, httpClient)
    const val IMAGE_NAME = "gcr.io/cloud-spanner-emulator/emulator"
    const val CONTAINER_NAME = "misk-spanner-testing"
    var image: String = "$IMAGE_NAME:latest"

    // A fresh container needs seconds to serve its first call. The health check used to back off in single
    // milliseconds, so it gave up long before the emulator was ready and let startup race it.
    private const val HEALTH_CHECK_ATTEMPTS = 20
    private const val HEALTH_CHECK_BACKOFF_BASE_MILLIS = 100L
    private const val HEALTH_CHECK_BACKOFF_MAX_MILLIS = 2_000L
    private const val ADMIN_ATTEMPTS = 10
    private const val ADMIN_BACKOFF_BASE_MILLIS = 100L
    private const val ADMIN_BACKOFF_MAX_MILLIS = 2_000L

    /** Codes the emulator returns while it is still coming up. Every other code is a real failure. */
    private val NOT_READY_ERROR_CODES = setOf(ErrorCode.UNAVAILABLE, ErrorCode.DEADLINE_EXCEEDED)

    fun pullImage() {
      if (imagePulled.get()) {
        return
      }
      synchronized(this) {
        if (imagePulled.get()) return

        val process =
          ProcessBuilder("bash", "-c", "docker pull $image")
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

  /** Pulls a Docker container containing the Google Spanner emulator. */
  fun pullImage(imageVersion: String? = null) {
    image = fullImageName(imageVersion)
    Companion.pullImage()
  }

  private fun doStart(imageVersion: String? = null) {
    val image =
      if (imageVersion != null) {
        fullImageName(imageVersion)
      } else {
        image
      }
    val spannerGrpcPort = ExposedPort.tcp(server.config.emulator.port)
    val spannerHttpPort = ExposedPort.tcp(server.config.emulator.port + 10)
    val ports = Ports()
    ports.bind(spannerGrpcPort, Ports.Binding.bindPort(9010)) // 9010 is the gRPC host inside the container
    ports.bind(spannerHttpPort, Ports.Binding.bindPort(9020)) // 9020 is the HTTP host inside the container
    val containerName = CONTAINER_NAME
    val runningContainer =
      docker.listContainersCmd().withNameFilter(listOf(containerName)).withLimit(1).exec().firstOrNull()

    if (runningContainer != null) {
      if (runningContainer.state != "running") {
        logger.info(
          "Existing Spanner named $containerName found in " +
            "state ${runningContainer.state}, force removing and restarting"
        )
        docker.removeContainerCmd(runningContainer.id).withForce(true).exec()
      } else if (runningContainer.image != image) {
        logger.info("Docker image does not match expected image. Force removing and restarting Docker container")
        docker.killContainerCmd(runningContainer.id)
        docker.removeContainerCmd(runningContainer.id)
      } else {
        logger.info("Using existing Spanner container named $containerName")
        stopContainerOnExit = false
        containerId = runningContainer.id
      }
    }

    if (containerId == null) {
      logger.info("Starting Spanner with command")
      containerId =
        docker
          .createContainerCmd(image)
          .withExposedPorts(spannerGrpcPort)
          .withExposedPorts(spannerHttpPort)
          .withHostConfig(HostConfig().withPortBindings(ports))
          .withTty(true)
          .withName(containerName)
          .exec()
          .id!!
      val containerId = containerId!!
      docker.startContainerCmd(containerId).exec()
      docker
        .logContainerCmd(containerId)
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
          limitAttempts(HEALTH_CHECK_ATTEMPTS) +
            binaryExponentialBackoff(HEALTH_CHECK_BACKOFF_BASE_MILLIS, HEALTH_CHECK_BACKOFF_MAX_MILLIS)
        ) {
          // The query will fail if the server is not responding
          client.instanceAdminClient.listInstances().values
        }
      }
    } catch (e: Exception) {
      throw Exception("Spanner server failed to start up in time", e)
    }
  }

  /**
   * Creates the configured instance and database, and waits out an emulator that is not ready to serve admin calls.
   *
   * An emulator that answers `listInstances` does not always accept admin writes yet, so [waitUntilHealthy] alone does
   * not prove readiness. Each step here reads before it writes, so the whole block is safe to repeat.
   */
  private fun createDatabase() {
    retryWhileUnavailable("create the instance and database") { getOrCreateDatabase(getOrCreateInstance()) }
  }

  private fun getOrCreateInstance(): Instance =
    try {
      client.instanceAdminClient.getInstance(config.instance_id)
    } catch (e: SpannerException) {
      // An unavailable emulator tells us nothing about whether the instance exists, so do not read the failure as
      // "absent" and create it. Hand it to the retry instead.
      if (e.isEmulatorUnavailable()) throw e

      client.instanceAdminClient
        .createInstance(
          InstanceInfo.newBuilder(InstanceId.of(config.project_id, config.instance_id))
            .setInstanceConfigId(InstanceConfigId.of(config.project_id, "emulator-config"))
            .build()
        )
        .get()
    }

  private fun getOrCreateDatabase(instance: Instance) {
    try {
      instance.getDatabase(config.database)
    } catch (e: SpannerException) {
      if (e.isEmulatorUnavailable()) throw e

      // Wait for the operation. An unawaited future can fail after startUp returns, which would hand tests a database
      // that does not exist and put the failure outside the retry.
      instance.createDatabase(config.database, listOf()).get()
    }
  }

  /**
   * Runs [block] until it succeeds, and backs off while the emulator reports itself unavailable. Any other failure
   * propagates on the first attempt.
   */
  private fun <T> retryWhileUnavailable(description: String, block: () -> T): T {
    var lastFailure: Exception? = null
    var backoffMillis = ADMIN_BACKOFF_BASE_MILLIS

    repeat(ADMIN_ATTEMPTS) {
      try {
        return block()
      } catch (e: Exception) {
        if (!e.isEmulatorUnavailable()) throw e

        lastFailure = e
        logger.info("Spanner emulator cannot $description yet. Retrying in $backoffMillis ms.")
        Thread.sleep(backoffMillis)
        backoffMillis = (backoffMillis * 2).coerceAtMost(ADMIN_BACKOFF_MAX_MILLIS)
      }
    }

    throw IllegalStateException("Spanner emulator did not $description in time", lastFailure)
  }

  /**
   * Reports whether this failure, or any cause below it, is the emulator refusing a call because it is not ready. The
   * walk down the causes matters: `OperationFuture.get` wraps the Spanner error in an `ExecutionException`.
   */
  private fun Throwable.isEmulatorUnavailable(): Boolean {
    var failure: Throwable? = this

    while (failure != null) {
      if (failure is SpannerException && failure.errorCode in NOT_READY_ERROR_CODES) return true
      failure = failure.cause
    }

    return false
  }

  /** Stops a Docker container running the Google Spanner emulator. */
  override fun shutDown() {
    client.close()
    logger.info(
      "Leaving Spanner docker container running in the background. " +
        "If you need to kill it because you messed up migrations or something use:" +
        "\n\tdocker kill $CONTAINER_NAME"
    )
  }

  override fun reset() = clearTables()

  fun clearTables() {
    val dataClient = client.getDatabaseClient(DatabaseId.of(config.project_id, config.instance_id, config.database))
    val tableNameQuery =
      dataClient
        .singleUseReadOnlyTransaction()
        .executeQuery(
          Statement.of(
            """
            SELECT
              table_name
            FROM
              information_schema.tables
            WHERE
              table_catalog = '' and table_schema = ''
            """
              .trimIndent()
          )
        )
    val tableNames: MutableList<String> = mutableListOf()
    while (tableNameQuery.next()) {
      tableNames.add(tableNameQuery.getString(0))
    }
    if (tableNames.size == 0) return
    dataClient.readWriteTransaction().run {
      it.batchUpdate(tableNames.map { tableName -> Statement.of("DELETE FROM ${tableName} WHERE true") })
    }
  }

  private fun fullImageName(imageVersion: String?): String {
    return "$IMAGE_NAME:${imageVersion ?: "latest"}"
  }

  private class SpannerServer(val config: SpannerConfig) {}
}
