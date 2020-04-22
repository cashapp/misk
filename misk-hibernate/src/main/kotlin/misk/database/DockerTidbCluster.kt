package misk.database

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.async.ResultCallbackTemplate
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import com.google.common.collect.Lists
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class TidbCluster(
  val resourceLoader: ResourceLoader,
  val config: DataSourceConfig
) {
  val httpPort = 10080
  val mysqlPort = 4000
  val configDir: Path

  init {
    configDir =
        Paths.get("/tmp/tidb_conf_${System.currentTimeMillis()}")
    Files.createDirectories(configDir)
    resourceLoader.copyTo("classpath:/misk/tidb", configDir)
    Runtime.getRuntime().addShutdownHook(
        thread(start = false) {
          configDir.toFile().deleteRecursively()
        })
  }

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
}

class DockerTidbCluster(
  val moshi: Moshi,
  val resourceLoader: ResourceLoader,
  val config: DataSourceConfig,
  val docker: DockerClient
) : DatabaseServer {
  val cluster = TidbCluster(resourceLoader = resourceLoader, config = config)

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

    const val SHA = "8fdd4033cbd879593e968dd0e044286c23c66418798da686526bfa162db9cad0"
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
    val confVolume = Volume("/etc/tidb")
    val cmd = arrayOf("-config=/etc/tidb/tidb.toml", "-config-strict")
    val httpPort = ExposedPort.tcp(cluster.httpPort)
    val mysqlPort = ExposedPort.tcp(cluster.mysqlPort)
    val ports = Ports()
    ports.bind(mysqlPort, Ports.Binding.bindPort(mysqlPort.port))
    ports.bind(httpPort, Ports.Binding.bindPort(httpPort.port))

    val containerName = "$CONTAINER_NAME"

    val runningContainer = docker.listContainersCmd()
        .withNameFilter(listOf(containerName))
        .withLimit(1)
        .exec()
        .firstOrNull()
    if (runningContainer != null) {
      if (runningContainer.state != "running") {
        logger.info("Existing TiDB cluster named $containerName found in " +
            "state ${runningContainer.state}, force removing and restarting")
        docker.removeContainerCmd(runningContainer.id).withForce(true).exec()
      } else {
        logger.info("Using existing TiDB cluster named $containerName")
        stopContainerOnExit = false
        containerId = runningContainer.id
      }
    }

    if (containerId == null) {
      logger.info("Starting TiDB cluster")
      containerId = docker.createContainerCmd(IMAGE)
          .withCmd(cmd.toList())
          .withVolumes(confVolume)
          .withBinds(Bind(cluster.configDir.toAbsolutePath().toString(), confVolume))
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
