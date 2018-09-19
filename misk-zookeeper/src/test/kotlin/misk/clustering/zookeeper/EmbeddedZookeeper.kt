package misk.clustering.zookeeper

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.async.ResultCallbackTemplate
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import misk.logging.getLogger
import java.util.concurrent.atomic.AtomicBoolean

class EmbeddedZookeeper(val basePort: Int) {
  private lateinit var containerId: String
  private val running = AtomicBoolean(false)

  val config = ZookeeperConfig(zk_connect = "127.0.0.1:$basePort")

  fun start() {
    if (!running.compareAndSet(false, true)) return

    val clientPort = ExposedPort.tcp(basePort)
    val peerPort = ExposedPort.tcp(basePort + 1)
    val leaderPort = ExposedPort.tcp(basePort + 2)
    val ports = Ports()
    ports.bind(peerPort, Ports.Binding.bindPort(PEER_PORT))
    ports.bind(clientPort, Ports.Binding.bindPort(CLIENT_PORT))
    ports.bind(leaderPort, Ports.Binding.bindPort(LEADER_PORT))

    val cmd = arrayOf("zkServer.sh", "start-foreground")

    log.info { "starting zookeeper with ${cmd.joinToString(" ")}" }

    containerId = docker.createContainerCmd("zookeeper:latest")
        .withCmd(cmd.toList())
        .withExposedPorts(clientPort)
        .withPortBindings(ports)
        .withTty(true)
        .exec().id

    Runtime.getRuntime().addShutdownHook(Thread { stop() })

    docker.startContainerCmd(containerId).exec()
    docker.logContainerCmd(containerId)
        .withStdErr(true)
        .withStdOut(true)
        .withFollowStream(true)
        .withSince(0)
        .exec(LogContainerResultCallback())
        .awaitStarted()

    log.info { "started zookeeper; container id=$containerId" }
  }

  fun stop() {
    if (!running.compareAndSet(true, false)) return

    docker.removeContainerCmd(containerId).withForce(true).exec()
    log.info { "killed zookeeper with container id $containerId" }
  }

  class LogContainerResultCallback : ResultCallbackTemplate<LogContainerResultCallback, Frame>() {
    override fun onNext(item: Frame) {
      String(item.payload).trim().split('\r', '\n').filter { it.isNotBlank() }.forEach {
        log.info(it)
      }
    }
  }

  companion object {
    private val log = getLogger<EmbeddedZookeeper>()
    private val docker: DockerClient = DockerClientBuilder.getInstance()
        .withDockerCmdExecFactory(NettyDockerCmdExecFactory())
        .build()

    const val PEER_PORT = 2888
    const val LEADER_PORT = 3888
    const val CLIENT_PORT = 2181
  }
}

fun main(args: Array<String>) {
  EmbeddedZookeeper(basePort = 28000).start()
}