package misk.clustering.etcd

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.async.ResultCallbackTemplate
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import misk.logging.getLogger
import java.util.concurrent.atomic.AtomicBoolean

class DockerEtcdCluster(private val basePort: Int) {
  private lateinit var containerId: String
  private val running = AtomicBoolean(false)

  val config = EtcdConfig(endpoints = listOf("http://localhost:$basePort"))

  fun start() {
    if (!running.compareAndSet(false, true)) return

    val clientPort = ExposedPort.tcp(basePort)
    val peerListenerPort = ExposedPort.tcp(basePort + 1)
    val ports = Ports()
    ports.bind(peerListenerPort, Ports.Binding.bindPort(peerListenerPort.port))
    ports.bind(clientPort, Ports.Binding.bindPort(clientPort.port))

    val cmd = arrayOf(
        "/usr/local/bin/etcd", "--data-dir=/etcd-data",
        "-name", "etcd0",
        "-advertise-client-urls", "http://0.0.0.0:${clientPort.port}",
        "-listen-client-urls", "http://0.0.0.0:${clientPort.port}",
        "-listen-peer-urls", "http://0.0.0.0:${peerListenerPort.port}",
        "-initial-cluster-token", "etcd-cluster-1",
        "-initial-cluster-state", "new"
    )

    log.info { "starting etcd with ${cmd.joinToString(" ")}" }

    containerId = docker.createContainerCmd("quay.io/coreos/etcd:latest")
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

    log.info { "started etcd; container id=$containerId" }
  }

  fun stop() {
    if (!running.compareAndSet(true, false)) return

    docker.removeContainerCmd(containerId).withForce(true).exec()
    log.info { "killed etcd with container id $containerId" }
  }

  class LogContainerResultCallback : ResultCallbackTemplate<LogContainerResultCallback, Frame>() {
    override fun onNext(item: Frame) {
      String(item.payload).trim().split('\r', '\n').filter { it.isNotBlank() }.forEach {
        log.info(it)
      }
    }
  }

  companion object {
    private val log = getLogger<DockerEtcdCluster>()
    private val docker: DockerClient = DockerClientBuilder.getInstance()
        .withDockerCmdExecFactory(NettyDockerCmdExecFactory())
        .build()
  }
}

fun main(args: Array<String>) {
  DockerEtcdCluster(basePort = 28000).start()
}