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
import misk.moshi.adapter
import mu.KotlinLogging
import okio.buffer
import okio.source
import java.nio.file.Files
import java.nio.file.Paths
import javax.inject.Singleton
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
}

internal class DockerVitessCluster(
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
    val httpPort = ExposedPort.tcp(27000)
    // TODO auto-allocate a port for grpc so they don't conflict
    val grpcPort = ExposedPort.tcp(cluster.config.port ?: 27001)
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
        "-keyspaces=$keyspacesArg",
        "-num_shards=$shardCounts"
    )

    StartVitessService.logger.info("Starting Vitess cluster with command: ${cmd.joinToString(" ")}")
    containerId = docker.createContainerCmd("vitess/base")
        .withCmd(cmd.toList())
        .withVolumes(schemaVolume)
        .withBinds(Bind(cluster.schemaDir.toAbsolutePath().toString(), schemaVolume))
        .withExposedPorts(httpPort, grpcPort)
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

@Singleton
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
  }
}

fun main(args: Array<String>) {
  val config = DataSourceConfig(type = DataSourceType.VITESS,
      vitess_schema_dir = ".")
  val docker: DockerClient = DockerClientBuilder.getInstance()
      .withDockerCmdExecFactory(NettyDockerCmdExecFactory())
      .build()
  val cluster = DockerVitessCluster(VitessCluster(config), docker)
  cluster.start()
  Runtime.getRuntime().addShutdownHook(thread(start = false) {
    cluster.stop()
  })
}