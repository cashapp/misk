package misk.database

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.core.async.ResultCallbackTemplate
import com.squareup.moshi.Moshi
import com.zaxxer.hikari.util.DriverDataSource
import misk.backoff.DontRetryException
import misk.backoff.ExponentialBackoff
import misk.backoff.retry
import misk.environment.Environment
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.jdbc.uniqueInt
import misk.resources.ResourceLoader
import mu.KotlinLogging
import java.sql.Connection
import java.sql.SQLException
import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean

class CockroachCluster(
  val name: String,
  val config: DataSourceConfig
) {
  /**
   * Connect to vtgate.
   */
  fun openConnection(): Connection = dataSource().connection

  private fun dataSource(): DriverDataSource {
    val jdbcUrl = config.withDefaults().buildJdbcUrl(
        Environment.TESTING)
    return DriverDataSource(
        jdbcUrl, config.type.driverClassName, Properties(),
        config.username, config.password)
  }

  val externalHttpPort = 26258
  val internalHttpPort = 8080
  val postgresPort = 26257
}

class DockerCockroachCluster(
  val name: String,
  val moshi: Moshi,
  val resourceLoader: ResourceLoader,
  val config: DataSourceConfig,
  val docker: DockerClient
) : DatabaseServer {
  val cluster: CockroachCluster

  private var containerId: String? = null

  private var isRunning = false
  private var stopContainerOnExit = true
  private var startupFailure: Exception? = null

  init {
    cluster = CockroachCluster(
        name = name,
        config = config)
  }

  override fun start() {
    val startupFailure = this.startupFailure
    if (startupFailure != null) {
      throw startupFailure
    }
    if (isRunning) {
      return
    }

    isRunning = true
    try {
      doStart()
    } catch (e: Exception) {
      this.startupFailure = e
      throw e
    }
  }

  companion object {
    val logger = KotlinLogging.logger {}

    const val SHA = "67f0547f1a989ebd119e5cbf903c8537556f574da20182454c036da63ea67c7d"
    const val IMAGE = "cockroachdb/cockroach@sha256:$SHA"
    const val CONTAINER_NAME = "misk-cockroach-testing"

    fun pullImage() {
      if (imagePulled.get()) {
        return
      }

      synchronized(this) {
        if (imagePulled.get()) {
          return
        }

        if (runCommand(
                "docker images --digests | grep -q $SHA || docker pull $IMAGE") != 0) {
          logger.warn("Failed to pull Cockroach docker image. Proceeding regardless.")
        }
        imagePulled.set(true)
      }
    }

    private val imagePulled = AtomicBoolean()
  }

  override fun pullImage() {
    DockerCockroachCluster.pullImage()
  }

  private fun doStart() {
    val httpPort = ExposedPort.tcp(cluster.internalHttpPort)
    if (cluster.config.type == DataSourceType.COCKROACHDB) {
      if (cluster.config.port != null && cluster.config.port != cluster.postgresPort) {
        throw RuntimeException(
            "Config port ${cluster.config.port} has to match Cockroach Docker container: ${cluster.postgresPort}")
      }
    }
    val postgresPort = ExposedPort.tcp(cluster.postgresPort)
    val ports = Ports()
    ports.bind(httpPort, Ports.Binding.bindPort(cluster.externalHttpPort))
    ports.bind(postgresPort, Ports.Binding.bindPort(postgresPort.port))

    val cmd = arrayOf(
        "start-single-node",
        "--insecure"
    )

    val containerName = "$CONTAINER_NAME"

    val runningContainer = docker.listContainersCmd()
        .withNameFilter(listOf(containerName))
        .withLimit(1)
        .exec()
        .firstOrNull()
    if (runningContainer != null) {
      if (runningContainer.state != "running") {
        logger.info("Existing Cockroach cluster named $containerName found in " +
            "state ${runningContainer.state}, force removing and restarting")
        docker.removeContainerCmd(runningContainer.id).withForce(true).exec()
      } else {
        logger.info("Using existing Cockroach cluster named $containerName")
        stopContainerOnExit = false
        containerId = runningContainer.id
      }
    }

    if (containerId == null) {
      logger.info(
          "Starting Cockroach cluster with command: ${cmd.joinToString(" ")}")
      containerId = docker.createContainerCmd(IMAGE)
          .withCmd(cmd.toList())
          .withExposedPorts(httpPort, postgresPort)
          .withPortBindings(ports)
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
    logger.info("Started Cockroach with container id $containerId")

    waitUntilHealthy()
    createDatabase()
  }

  private fun waitUntilHealthy() {
    try {
      retry(20, ExponentialBackoff(
          Duration.ofSeconds(1),
          Duration.ofSeconds(5))) {
        cluster.openConnection().use { c ->
          val result =
              c.createStatement().executeQuery("SELECT 1").uniqueInt()
          check(result == 1)
        }
      }
    } catch (e: DontRetryException) {
      throw Exception(e.message)
    } catch (e: Exception) {
      throw Exception("Cockroach cluster failed to start up in time", e)
    }
  }

  private fun createDatabase() {
    cluster.openConnection().use { c ->
      c.createStatement().use { statement ->
        try {
          // TODO might need something like "does not exist" if we're reusing clusters
          statement.addBatch("CREATE DATABASE ${config.database}")
          statement.executeBatch()
        } catch (e: SQLException) {
          if (!e.message!!.contains("already exists")) {
            throw e
          }
          Unit
        }
      }
    }
  }

  override fun stop() {
    logger.info("Leaving Cockroach docker container running in the background. " +
        "If you need to kill it because you messed up migrations or something use:" +
        "\n\tdocker kill $CONTAINER_NAME")
  }

  class LogContainerResultCallback : ResultCallbackTemplate<LogContainerResultCallback, Frame>() {
    override fun onNext(item: Frame) {
      logger.info(String(item.payload).trim())
    }
  }
}
