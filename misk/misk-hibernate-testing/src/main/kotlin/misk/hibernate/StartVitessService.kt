package misk.hibernate

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.*
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.util.concurrent.AbstractIdleService
import com.google.gson.Gson
import mu.KotlinLogging
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlin.streams.toList

internal class DockerVitessCluster(
  val config: DataSourceConfig,
  val docker: DockerClient,
  val gson: Gson
) {
  private var containerId: String? = null

  fun isRunning() = containerId != null

  fun start() {
    if (isRunning()) {
      return
    }

    val schemaDir = Paths.get(config.vitess_schema_dir)
    check(Files.isDirectory(schemaDir)) {
      "can't find directory ${config.vitess_schema_dir}"
    }
    val keyspaceDirs = Files.list(schemaDir).toList().filter { Files.isDirectory(it) }
    val keyspaces =
        keyspaceDirs.map { it.fileName }.joinToString(",")
    val shardCounts = keyspaceDirs.map { it.resolve("vschema.json") }
        .map { gson.fromJson(FileReader(it.toFile()), Map::class.java) }
        // Defaulting to 2 shards for sharded keyspaces,
        // maybe this should be configurable at some point?
        .map { if (it["sharded"] as Boolean) "2" else "1" }
        .joinToString(",")

    val schemaVolume = Volume("/vt/src/vitess.io/vitess/schema")
    val httpPort = ExposedPort.tcp(27000)
    // TODO auto-allocate a port for grpc so they don't conflict
    val grpcPort = ExposedPort.tcp(config.port ?: 27001)
    val ports = Ports()
    ports.bind(grpcPort, Ports.Binding.bindPort(grpcPort.port))
    ports.bind(httpPort, Ports.Binding.bindPort(httpPort.port))

    val cmd = arrayOf(
        "/vt/bin/vttestserver",
        "-alsologtostderr",
        // TODO auto-allocate a port and either provide a config or update it in place?
        "-port=27000",
        "-web_dir=web/vtctld/app",
        "-web_dir2=web/vtctld2/app",
        "-mysql_bind_host=0.0.0.0",
        "-schema_dir=schema",
        "-keyspaces=$keyspaces",
        "-num_shards=$shardCounts"
    )

    StartVitessService.logger.info("Starting Vitess cluster with command: ${cmd.joinToString(" ")}")
    containerId = docker.createContainerCmd("vitess/base")
        .withCmd(cmd.toList())
        .withVolumes(schemaVolume)
        .withBinds(Bind(schemaDir.toAbsolutePath().toString(), schemaVolume))
        .withExposedPorts(httpPort, grpcPort)
        .withPortBindings(ports)
        .exec().id
    docker.startContainerCmd(containerId!!).exec()
    StartVitessService.logger.info("Started Vitess with container id $containerId")
  }

  fun stop() {
    if (!isRunning()) {
      return
    }

    docker.killContainerCmd(containerId!!).exec()
    StartVitessService.logger.info("Killed Vitess cluster with container id $containerId")
  }
}

internal class StartVitessService(val config: DataSourceConfig) : AbstractIdleService() {
  override fun startUp() {
    if (config.type != DataSourceType.VITESS) {
      // We only start up Vitess if Vitess has been configured
      return
    }

    clusters[config].start()
  }

  override fun shutDown() {
  }

  companion object {
    val logger = KotlinLogging.logger {}
    val docker: DockerClient = DockerClientBuilder.getInstance()
        .withDockerCmdExecFactory(NettyDockerCmdExecFactory())
        .build()
    val gson = Gson()

    /**
     * Global cache of running vitess clusters.
     */
    val clusters = CacheBuilder.newBuilder()
        .removalListener<DataSourceConfig, DockerVitessCluster> { it.value.stop() }
        .build(CacheLoader.from { config: DataSourceConfig? ->
          DockerVitessCluster(config!!, docker, gson)
        })

    /**
     * Shut down the cached clusters on JVM exit.
     */
    init {
      Runtime.getRuntime().addShutdownHook(Thread {
        clusters.invalidateAll()
      })
    }
  }
}
