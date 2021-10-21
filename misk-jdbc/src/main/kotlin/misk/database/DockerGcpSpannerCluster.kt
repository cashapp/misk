package misk.database

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Bind
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
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.jdbc.uniqueInt
import misk.resources.ResourceLoader
import mu.KotlinLogging
import wisp.deployment.TESTING
import java.sql.Connection
import java.time.Duration
import java.util.Properties

class GcpSpannerCluster(
  val name: String,
  val config: DataSourceConfig,
) {

  val httpPort = 9020
  val spannerPort = 9010

  fun openConnection(): Connection = dataSource().connection

  private fun dataSource(): DriverDataSource {
    val jdbcUrl = config.withDefaults().buildJdbcUrl(TESTING)
    return DriverDataSource(
      jdbcUrl, config.type.driverClassName, Properties(),
      config.username, config.password)
  }

}

class DockerGcpSpannerCluster(
  val name: String,
  val moshi: Moshi,
  val resourceLoader: ResourceLoader,
  val config: DataSourceConfig,
  val docker: DockerClient
) : DatabaseServer {
  val cluster: GcpSpannerCluster

  private var isRunning = false
  private var stopContainerOnExit = true
  private var startupFailure: Exception? = null
  private var containerId: String? = null

  private fun containerName() = CONTAINER_NAME


  init {
    cluster = GcpSpannerCluster(
      name = name,
      config = config
    )
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

  override fun pullImage() {
    TODO("Not yet implemented")
  }

  private fun doStart() {
    if (cluster.config.type == DataSourceType.GCP_SPANNER) {
      if (cluster.config.port != null && cluster.config.port != cluster.spannerPort) {
        throw RuntimeException(
          "Config port ${cluster.config.port} has to match GCP Spanner Docker container: " +
            "${cluster.spannerPort}"
        )
      }
    }
    val httpPort = ExposedPort.tcp(cluster.httpPort)
    val spannerPort = ExposedPort.tcp(cluster.spannerPort)
    val ports = Ports()
    ports.bind(spannerPort, Ports.Binding.bindPort(spannerPort.port))
    ports.bind(httpPort, Ports.Binding.bindPort(httpPort.port))

    // Kill and remove container that don't match our requirements
    var matchingContainer: Container? = null
    val runningContainer = docker.listContainersCmd()
      .withNameFilter(listOf(containerName()))
      .withLimit(1)
      .exec()
      .firstOrNull()
    if (runningContainer != null) {
      val mismatches = containerMismatches(runningContainer)
      if (!mismatches.isEmpty()) {
        DockerTidbCluster.logger.info {
          "container named ${runningContainer.name()} does not match our requirements, " +
            "force removing and starting a new one: ${mismatches.joinToString(", ")}"
        }
        docker.removeContainerCmd(runningContainer.id).withForce(true).exec()
      } else {
        matchingContainer = runningContainer
      }
    }

    //val cmd = "docker run"

    containerId = matchingContainer?.id

    if (containerId == null) {
      logger.info("Starting GCP Spanner cluster")
      stopContainerOnExit = true
      containerId = docker.createContainerCmd(IMAGE)
        .withExposedPorts(spannerPort, httpPort)
        .withPortBindings(ports)
        .withTty(true)
        .withName(containerName())
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
    DockerTidbCluster.logger.info("Started TiDB with container id $containerId")

    waitUntilHealthy()
    cluster.openConnection().use { c ->
      c.createStatement().executeUpdate("SET GLOBAL time_zone = '+00:00'")
    }
  }

  override fun stop() {
    logger.info(
      "Leaving GCP Spanner docker container running in the background. " +
        "If you need to kill it because you messed up migrations or something use:" +
        "\n\tdocker kill ${CONTAINER_NAME}"
    )

    val containerId = containerId
      if (containerId != null) {
        docker.killContainerCmd(containerId);
      }
  }

  /**
   * Check if the container is a container that we can use for our tests. If it is not return a
   * description of the mismatch.
   */
  private fun containerMismatches(container: Container): List<String> = listOfNotNull(
    shouldMatch("container name", container.name(), containerName()),
    shouldMatch("container state", container.state, "running"),
    shouldMatch("container image", container.image, DockerTidbCluster.IMAGE)
  )

  private fun shouldMatch(description: String, actual: Any, expected: Any): String? =
    if (expected != actual) {
      "$description \"${actual}\" does not match \"${expected}\""
    } else {
      null
    }

  /**
   * Return the single name of a container and strip away the prefix /
   */
  private fun Container.name(): String {
    val name = names.single()
    return if (name.startsWith("/")) name.substring(1) else name
  }

  private fun waitUntilHealthy() {
    try {
      retry(
        20, ExponentialBackoff(
        Duration.ofSeconds(1),
        Duration.ofSeconds(5)
      )
      ) {
        cluster.openConnection().use { c ->
          val result =
            c.createStatement().executeQuery("SELECT 1").uniqueInt()
          check(result == 1)
        }
      }
    } catch (e: DontRetryException) {
      throw Exception(e.message)
    } catch (e: Exception) {
      throw Exception("GCP Spanner cluster failed to start up in time", e)
    }
  }

  companion object {
    val logger = KotlinLogging.logger {}

    const val SHA = "ef0fd2ec74bb17b6c31e5010fb699fd0009b9829721d5159e6a11b6a40f881f1" // How do we update these?
    const val IMAGE = "gcr.io/cloud-spanner-emulator/emulator@sha256:${SHA}"
    const val CONTAINER_NAME = "misk-gcp-spanner-testing"
  }

  class LogContainerResultCallback : ResultCallbackTemplate<LogContainerResultCallback, Frame>() {
    override fun onNext(item: Frame) {
      logger.info(String(item.payload).trim())
    }
  }
}
