package misk.clustering.zookeeper

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Ports
import misk.containers.Composer
import misk.containers.Container

class EmbeddedZookeeper(val basePort: Int) {

  private val composer: Composer

  init {
    val clientPort = ExposedPort.tcp(CLIENT_PORT)
    val peerPort = ExposedPort.tcp(PEER_PORT)
    val leaderPort = ExposedPort.tcp(LEADER_PORT)
    val cmd = arrayOf("zkServer.sh", "start-foreground")
    val container = Container {
      this
          .withImage("zookeeper:3.5.4-beta")
          .withName("zookeeper")
          .withCmd(cmd.toList())
          .withExposedPorts(clientPort, peerPort, leaderPort)
          .withPortBindings(Ports().apply {
            bind(clientPort, Ports.Binding.bindPort(basePort))
            bind(peerPort, Ports.Binding.bindPort(basePort + 1))
            bind(leaderPort, Ports.Binding.bindPort(basePort + 2))
          })
    }
    composer = Composer("e-zk", container)
  }

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
