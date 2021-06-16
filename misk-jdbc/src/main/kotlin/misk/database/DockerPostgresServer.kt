package misk.database

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.core.async.ResultCallbackTemplate
import com.zaxxer.hikari.util.DriverDataSource
import misk.backoff.DontRetryException
import misk.backoff.ExponentialBackoff
import misk.backoff.retry
import misk.jdbc.DataSourceConfig
import mu.KotlinLogging
import wisp.deployment.TESTING
import java.sql.Connection
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class DockerPostgresServer(
  val config: DataSourceConfig,
  val docker: DockerClient
) : DatabaseServer {
  private val server: PostgresServer

  private var containerId: String? = null

  private var isRunning = false
  private var stopContainerOnExit = true
  private var startupFailure: Exception? = null

  init {
    server = PostgresServer(config = config)
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

    const val SHA = "7421834e2eae283f829d3face39acba2e8ffbe24be756f7cabdfe778e7bfec57"
    const val IMAGE = "postgres@sha256:$SHA"
    const val CONTAINER_NAME = "misk-postgres-testing"

    fun pullImage() {
      if (imagePulled.get()) {
        return
      }

      synchronized(this) {
        if (imagePulled.get()) {
          return
        }

        if (runCommand(
            "docker images --digests | grep -q $SHA || docker pull $IMAGE"
          ) != 0) {
          logger.warn("Failed to pull Postgres docker image. Proceeding regardless.")
        }
        imagePulled.set(true)
      }
    }

    private val imagePulled = AtomicBoolean()
  }

  override fun pullImage() {
    DockerPostgresServer.pullImage()
  }

  private fun doStart() {
    val postgresPort = ExposedPort.tcp(server.postgresPort)
    val ports = Ports()
    ports.bind(postgresPort, Ports.Binding.bindPort(postgresPort.port))

    val containerName = CONTAINER_NAME

    val runningContainer = docker.listContainersCmd()
      .withNameFilter(listOf(containerName))
      .withLimit(1)
      .exec()
      .firstOrNull()
    if (runningContainer != null) {
      if (runningContainer.state != "running") {
        logger.info(
          "Existing Postgres named $containerName found in " +
            "state ${runningContainer.state}, force removing and restarting"
        )
        docker.removeContainerCmd(runningContainer.id).withForce(true).exec()
      } else {
        logger.info("Using existing Postgres container named $containerName")
        stopContainerOnExit = false
        containerId = runningContainer.id
      }
    }

    if (containerId == null) {
      logger.info("Starting Postgres with command")
      containerId = docker.createContainerCmd(IMAGE)
        .withEnv("POSTGRES_PASSWORD=password")
        .withCmd("-d postgres")
        .withExposedPorts(postgresPort)
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
    logger.info("Started Postgres with container id $containerId")

    waitUntilHealthy()
    createDatabase()
  }

  private fun waitUntilHealthy() {
    try {
      retry(
        20, ExponentialBackoff(
        Duration.ofSeconds(1),
        Duration.ofSeconds(5)
      )
      ) {
        server.openConnection().use { c ->
          val resultSet =
            c.createStatement().executeQuery("SELECT COUNT(*) as count FROM pg_catalog.pg_database")
          resultSet.next()
          check(resultSet.getInt("count") > 0)
        }
      }
    } catch (e: DontRetryException) {
      throw Exception(e.message)
    } catch (e: Exception) {
      throw Exception("Postgres server failed to start up in time", e)
    }
  }

  private fun createDatabase() {
    server.openConnection().use { c ->
      val statement = c.createStatement()
      val databaseCountResultSet = statement.executeQuery(
        "SELECT COUNT(*) as count FROM pg_catalog.pg_database WHERE datname = '${config.database}'"
      )
      databaseCountResultSet.next()

      if (databaseCountResultSet.getInt("count") == 0) {
        statement.addBatch("CREATE DATABASE ${config.database}")
        statement.executeBatch()
      }
    }
  }

  override fun stop() {
    logger.info(
      "Leaving Postgres docker container running in the background. " +
        "If you need to kill it because you messed up migrations or something use:" +
        "\n\tdocker kill $CONTAINER_NAME"
    )
  }

  class LogContainerResultCallback : ResultCallbackTemplate<LogContainerResultCallback, Frame>() {
    override fun onNext(item: Frame) {
      logger.info(String(item.payload).trim())
    }
  }

  private class PostgresServer(
    val config: DataSourceConfig
  ) {
    fun openConnection(): Connection = dataSource().connection

    private fun dataSource(): DriverDataSource {
      val jdbcUrl = config.withDefaults().copy(database = "postgres").buildJdbcUrl(TESTING)
      return DriverDataSource(
        jdbcUrl,
        config.type.driverClassName,
        Properties(),
        config.username,
        config.password
      )
    }

    val postgresPort = 5432
  }
}
