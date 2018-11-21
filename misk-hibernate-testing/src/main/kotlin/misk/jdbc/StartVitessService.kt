package misk.jdbc

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.async.ResultCallbackTemplate
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.util.concurrent.AbstractIdleService
import com.squareup.moshi.Moshi
import com.zaxxer.hikari.util.DriverDataSource
import misk.backoff.ExponentialBackoff
import misk.backoff.retry
import misk.environment.Environment
import misk.moshi.adapter
import mu.KotlinLogging
import okio.buffer
import okio.source
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.streams.toList

class Keyspace(val sharded: Boolean) {
  // Defaulting to 2 shards for sharded keyspaces,
  // maybe this should be configurable at some point?
  fun shardCount() = if (sharded) 2 else 1
}

class VitessCluster(val config: DataSourceConfig, moshi: Moshi = Moshi.Builder().build()) {
  val schemaDir = Paths.get(config.vitess_schema_dir)

  init {
    check(Files.isDirectory(schemaDir)) {
      "can't find directory ${schemaDir}"
    }
  }

  val keyspaceAdapter = moshi.adapter<Keyspace>()

  fun keyspaces(): Map<String, Keyspace> {
    val keyspaceDirs = Files.list(schemaDir).toList().filter { Files.isDirectory(it) }
    return keyspaceDirs.associateBy(
        { it.fileName.toString() },
        { keyspaceAdapter.fromJson(it.resolve("vschema.json").source().buffer())!! })
  }

  /**
   * Connect to vtgate.
   */
  fun openVtgateConnection() = dataSource().connection

  /**
   * Connect to the underlying MySQL database, bypassing Vitess entirely.
   */
  fun openMysqlConnection() = mysqlDataSource().connection

  private fun dataSource(): DriverDataSource {
    val jdbcUrl = config.type.buildJdbcUrl(config, Environment.TESTING)
    return DriverDataSource(
        jdbcUrl, config.type.driverClassName, Properties(), config.username, config.password)
  }

  private fun mysqlConfig() =
      DataSourceConfig(
          type = DataSourceType.MYSQL,
          host = "127.0.0.1",
          username = "vt_dba",
          port = mysqlPort
      )

  private fun mysqlDataSource(): DriverDataSource {
    val config = mysqlConfig()
    val jdbcUrl = config.type.buildJdbcUrl(config, Environment.TESTING)
    return DriverDataSource(
        jdbcUrl, config.type.driverClassName, Properties(), config.username, config.password)
  }

  val httpPort = 27000
  val grpcPort = httpPort + 1
  val mysqlPort = httpPort + 2
  val vtgateMysqlPort = httpPort + 3
}

class DockerVitessCluster(
  val cluster: VitessCluster,
  val docker: DockerClient
) {
  private var containerId: String? = null

  private var isRunning = false

  fun start() {
    if (isRunning) {
      return
    }

    isRunning = true

    val keyspaces = cluster.keyspaces()
    val keyspacesArg = keyspaces.keys.map { it }.joinToString(",")
    val shardCounts = keyspaces.values.map { it.shardCount() }.joinToString(",")

    val schemaVolume = Volume("/vt/src/vitess.io/vitess/schema")
    val httpPort = ExposedPort.tcp(cluster.httpPort)
    if (cluster.config.port != null && cluster.config.port != cluster.grpcPort) {
      throw RuntimeException(
          "Config port ${cluster.config.port} has to match Vitess Docker container: ${cluster.grpcPort}")
    }
    val grpcPort = ExposedPort.tcp(cluster.grpcPort)
    val mysqlPort = ExposedPort.tcp(cluster.mysqlPort)
    val vtgateMysqlPort = ExposedPort.tcp(cluster.vtgateMysqlPort)
    val ports = Ports()
    ports.bind(grpcPort, Ports.Binding.bindPort(grpcPort.port))
    ports.bind(httpPort, Ports.Binding.bindPort(httpPort.port))
    ports.bind(mysqlPort, Ports.Binding.bindPort(mysqlPort.port))
    ports.bind(vtgateMysqlPort, Ports.Binding.bindPort(vtgateMysqlPort.port))

    val cmd = arrayOf(
        "/vt/bin/vttestserver",
        "-alsologtostderr",
        "-port=" + httpPort.port,
        "-web_dir=web/vtctld/app",
        "-web_dir2=web/vtctld2/app",
        "-mysql_bind_host=0.0.0.0",
        "-data_dir=/vt/vtdataroot",
        "-schema_dir=schema",
        "-keyspaces=$keyspacesArg",
        "-num_shards=$shardCounts"
    )

    StartVitessService.logger.info("Starting Vitess cluster with command: ${cmd.joinToString(" ")}")
    containerId = docker.createContainerCmd("vitess/base")
        .withCmd(cmd.toList())
        .withVolumes(schemaVolume)
        .withBinds(Bind(cluster.schemaDir.toAbsolutePath().toString(), schemaVolume))
        .withExposedPorts(httpPort, grpcPort, mysqlPort, vtgateMysqlPort)
        .withPortBindings(ports)
        .withTty(true)
        .exec().id

    val containerId = containerId!!
    docker.startContainerCmd(containerId).exec()
    docker.logContainerCmd(containerId)
        .withStdErr(true)
        .withStdOut(true)
        .withFollowStream(true)
        .withSince(0)
        .exec(LogContainerResultCallback())
        .awaitStarted()
    StartVitessService.logger.info("Started Vitess with container id $containerId")

    waitUntilHealthy()

    grantExternalAccessToDbaUser()

    turnOnGeneralLog()
  }

  private fun waitUntilHealthy() {
    retry(10, ExponentialBackoff(Duration.ofMillis(20), Duration.ofMillis(1000))) {
      cluster.openVtgateConnection().use { c ->
        try {
          val result =
              c.createStatement().executeQuery("SELECT 1 FROM dual").uniqueResult { it.getInt(1) }
          check(result == 1)
        } catch (e: Exception) {
          val message = e.message
          if (message?.contains("table dual not found") == true) {
            throw RuntimeException(
                "Something is wrong with your vschema and unfortunately vtcombo does not " +
                    "currently have good error reporting on this. Please inspect the logs or your " +
                    "vschema to see if you can find the error.")
          } else {
            throw e;
          }
        }
      }
    }
  }

  /**
   * Grants external access to the vt_dba user so that we can access the general_log file.
   */
  private fun grantExternalAccessToDbaUser() {
    val exec = docker.execCreateCmd(containerId!!)
        .withAttachStderr(true)
        .withAttachStdout(true)
        .withCmd("mysql",
            "-S", "/vt/vtdataroot/vt_0000000001/mysql.sock",
            "-u", "root",
            "mysql",
            "-e", "grant all on *.* to 'vt_dba'@'%'")
        .exec()

    docker.execStartCmd(exec.id)
        .exec(LogContainerResultCallback())
        .awaitCompletion()

    val exitCode = docker.inspectExecCmd(exec.id).exec().exitCode
    if (exitCode != 0) {
      throw RuntimeException("Command failed, see log for details")
    }
  }

  /**
   * Turn on MySQL general_log so that we can inspect it in the VitessScaleSafetyChecks detectors.
   */
  private fun turnOnGeneralLog() {
    cluster.openMysqlConnection().use { connection ->
      connection.createStatement().use { statement ->
        statement.execute("SET GLOBAL log_output = 'TABLE'")
        statement.execute("SET GLOBAL general_log = 1")
      }
    }
  }

  fun stop() {
    if (!isRunning) {
      return
    }
    isRunning = false

    docker.removeContainerCmd(containerId!!).withForce(true).exec()
    StartVitessService.logger.info("Killed Vitess cluster with container id $containerId")
  }
}

class LogContainerResultCallback : ResultCallbackTemplate<LogContainerResultCallback, Frame>() {
  override fun onNext(item: Frame) {
    StartVitessService.logger.info(String(item.payload).trim())
  }
}

class StartVitessService(val config: DataSourceConfig) : AbstractIdleService() {
  init {
    // We need to do this outside of the service start up because this takes a really long time
    // the first time you do it. After that it's really fast though.
    if (runCommand("docker pull vitess/base:latest") != 0) {
      logger.warn("Failed to pull Vitess docker image. Proceeding regardless.")
    }
  }

  override fun startUp() {
    if (config.type != DataSourceType.VITESS) {
      // We only start up Vitess if Vitess has been configured
      return
    }

    clusters[config].start()
  }

  fun cluster() = clusters[config]!!.cluster

  override fun shutDown() {
  }

  companion object {
    val logger = KotlinLogging.logger {}
    val docker: DockerClient = DockerClientBuilder.getInstance()
        .withDockerCmdExecFactory(NettyDockerCmdExecFactory())
        .build()

    /**
     * Global cache of running vitess clusters.
     */
    val clusters = CacheBuilder.newBuilder()
        .removalListener<DataSourceConfig, DockerVitessCluster> { it.value.stop() }
        .build(CacheLoader.from { config: DataSourceConfig? ->
          config?.let { DockerVitessCluster(VitessCluster(config), docker) }
        })

    /**
     * Shut down the cached clusters on JVM exit.
     */
    init {
      Runtime.getRuntime().addShutdownHook(Thread {
        clusters.invalidateAll()
      })
    }

    fun runCommand(command : String): Int {
      logger.info(command)
      val process = ProcessBuilder(*command.split(" ").toTypedArray())
          .redirectOutput(ProcessBuilder.Redirect.INHERIT)
          .redirectError(ProcessBuilder.Redirect.INHERIT)
          .start()
      process.waitFor(60, TimeUnit.MINUTES)
      return process.exitValue()
    }
  }
}

/**
 * Runs a Vitess cluster based on the current working directory.
 */
fun main(args: Array<String>) {
  val config = DataSourceConfig(type = DataSourceType.VITESS, vitess_schema_dir = ".")
  val docker: DockerClient = DockerClientBuilder.getInstance()
      .withDockerCmdExecFactory(NettyDockerCmdExecFactory())
      .build()
  val cluster = DockerVitessCluster(VitessCluster(config), docker)
  cluster.start()
  Runtime.getRuntime().addShutdownHook(thread(start = false) {
    cluster.stop()
  })
}