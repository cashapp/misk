package misk.clustering.zookeeper

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.async.ResultCallbackTemplate
import com.github.dockerjava.core.command.WaitContainerResultCallback
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import misk.logging.getLogger
import java.util.concurrent.atomic.AtomicBoolean

class EmbeddedZookeeper(val basePort: Int) {
  private lateinit var containerId: String
  private val running = AtomicBoolean(false)

  fun start() {
    if (!running.compareAndSet(false, true)) return

    val clientPort = ExposedPort.tcp(CLIENT_PORT)
    val peerPort = ExposedPort.tcp(PEER_PORT)
    val leaderPort = ExposedPort.tcp(LEADER_PORT)
    val cmd = arrayOf("zkServer.sh", "start-foreground")

    log.info { "starting zookeeper with ${cmd.joinToString(" ")}" }

    // NB(mmihic): curator is only compatible with 3.5.X
    containerId = docker.createContainerCmd("zookeeper:3.5.4-beta")
        .withCmd(cmd.toList())
        .withExposedPorts(clientPort, peerPort, leaderPort)
        .withPortBindings(Ports().apply {
          bind(clientPort, Ports.Binding.bindPort(basePort))
          bind(peerPort, Ports.Binding.bindPort(basePort + 1))
          bind(leaderPort, Ports.Binding.bindPort(basePort + 2))
        })
        .withTty(true)
        .exec()
        .id

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

    log.info { "killing zookeeper with container id $containerId" }
    docker.removeContainerCmd(containerId).withForce(true).exec()

    try {
      log.info { "waiting for zookeeper service to terminate" }
      docker.waitContainerCmd(containerId).exec(WaitContainerResultCallback()).awaitCompletion()
    } catch (e: NotFoundException) {
      // this is ok, just meant that the container already terminated before we tried to wait
    } catch (th: Throwable) {
      log.error(th) { "could not kill zookeeper with container id $containerId" }
    }

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