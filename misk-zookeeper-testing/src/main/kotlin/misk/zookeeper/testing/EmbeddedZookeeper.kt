package misk.zookeeper.testing

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Ports
import wisp.containers.Composer
import wisp.containers.Container

class EmbeddedZookeeper(val basePort: Int) {

  private val clientPort = ExposedPort.tcp(CLIENT_PORT)
  private val peerPort = ExposedPort.tcp(PEER_PORT)
  private val leaderPort = ExposedPort.tcp(LEADER_PORT)
  private val composer = Composer("e-zk", Container({
    withImage("zookeeper:3.5.9")
      .withName("zookeeper")
      .withCmd(listOf("zkServer.sh", "start-foreground"))
      .withExposedPorts(clientPort, peerPort, leaderPort)
      .withPortBindings(Ports().apply {
        bind(clientPort, Ports.Binding.bindPort(basePort))
        bind(peerPort, Ports.Binding.bindPort(basePort + 1))
        bind(leaderPort, Ports.Binding.bindPort(basePort + 2))
      })
  }, { docker, id ->
    // Provide zoo.cfg and certs to run ZK with mTLS enabled.
    val confPath = EmbeddedZookeeper::class.java.getResource("/zookeeper").path
    docker.copyArchiveToContainerCmd(id)
      .withHostResource(confPath)
      .withDirChildrenOnly(true)
      .withRemotePath("/conf")
      .exec()
  }))

  fun start() {
    composer.start()
  }

  fun stop() {
    composer.stop()
  }

  companion object {
    const val PEER_PORT = 2888
    const val LEADER_PORT = 3888
    const val CLIENT_PORT = 2181
  }
}

fun main(args: Array<String>) {
  EmbeddedZookeeper(basePort = 28000).start()
}
