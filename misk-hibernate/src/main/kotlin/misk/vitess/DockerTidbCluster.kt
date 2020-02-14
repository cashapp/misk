package misk.vitess

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Container
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
import mu.KotlinLogging
import java.sql.Connection
import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean

class TidbCluster(
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

  val httpPort = 10080
  val mysqlPort = 4000
}

class DockerTidbCluster(
  val moshi: Moshi,
  val config: DataSourceConfig,
  val docker: DockerClient
) : DatabaseServer {
  val cluster = TidbCluster(config = config)

  private var containerId: String? = null

  private var isRunning = false
  private var stopContainerOnExit = true
  private var startupFailure: Exception? = null

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

    const val SHA = "35d1de74583d84d9de2e4defa9e97fa9f13f17245f6f5cee41d2d340e2c7df72"
    const val IMAGE = "pingcap/tidb@sha256:$SHA"
    const val CONTAINER_NAME = "misk-tidb-testing"

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
          logger.warn("Failed to pull TiDB docker image. Proceeding regardless.")
        }
        imagePulled.set(true)
      }
    }

    private val imagePulled = AtomicBoolean()
  }

  override fun pullImage() {
    DockerTidbCluster.pullImage()
  }

  private fun doStart() {
    if (cluster.config.type == DataSourceType.TIDB) {
      if (cluster.config.port != null && cluster.config.port != cluster.mysqlPort) {
        throw RuntimeException(
            "Config port ${cluster.config.port} has to match Tidb Docker container: ${cluster.mysqlPort}")
      }
    }
    val httpPort = ExposedPort.tcp(cluster.httpPort)
    val mysqlPort = ExposedPort.tcp(cluster.mysqlPort)
    val ports = Ports()
    ports.bind(mysqlPort, Ports.Binding.bindPort(mysqlPort.port))
    ports.bind(httpPort, Ports.Binding.bindPort(httpPort.port))

    val containerName = "$CONTAINER_NAME"

    val allContainers = docker.listContainersCmd().withShowAll(true).exec()
    val matchingContainer = allContainers.filter { container ->
      container.name() == containerName
    }.singleOrNull()

    // Kill and remove container that don't match our requirements
    if (matchingContainer != null) {
      val mismatches = containerMismatches(matchingContainer)
      if (mismatches.isNotEmpty()) {
        DockerVitessCluster.logger.info {
          "Container named ${matchingContainer.name()} does not match our requirements, " +
              "force removing and starting a new one: ${mismatches.joinToString(", ")}"
        }
        docker.removeContainerCmd(matchingContainer.id).withForce(true).exec()
      } else {
        containerId = matchingContainer.id
      }
    }

    if (containerId == null) {
      logger.info("Starting TiDB cluster")
      containerId = docker.createContainerCmd(IMAGE)
          .withExposedPorts(mysqlPort, httpPort)
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
    logger.info("Started TiDB with container id $containerId")

    waitUntilHealthy()
    cluster.openConnection().use { c ->
      c.createStatement().executeUpdate("SET GLOBAL time_zone = '+00:00'")
    }
  }

  /**
   * Check if the container is a container that we can use for our tests. If it is not return a
   * description of the mismatch.
   */
  private fun containerMismatches(container: Container): List<String> = listOfNotNull(
      shouldMatch("container name", container.name(), CONTAINER_NAME),
      shouldMatch("container state", container.state, "running"),
      shouldMatch("container image", container.image, IMAGE)
  )

  private fun shouldMatch(description: String, actual: Any, expected: Any): String? =
      if (expected != actual) {
        "$description \"${actual}\" does not match \"${expected}\""
      } else {
        null
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
      throw Exception("TiDB cluster failed to start up in time", e)
    }
  }

  override fun stop() {
    logger.info("Leaving TiDB docker container running in the background. " +
        "If you need to kill it because you messed up migrations or something use:" +
        "\n\tdocker kill ${CONTAINER_NAME}")
  }

  class LogContainerResultCallback : ResultCallbackTemplate<LogContainerResultCallback, Frame>() {
    override fun onNext(item: Frame) {
      logger.info(String(item.payload).trim())
    }
  }
}
