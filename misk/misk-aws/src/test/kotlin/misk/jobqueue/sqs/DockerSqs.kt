package misk.jobqueue.sqs

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Ports
import com.google.common.util.concurrent.AbstractIdleService
import misk.containers.Composer
import misk.containers.Container
import misk.jobqueue.sqs.DockerSqs.Companion.CLIENT_PORT
import javax.inject.Singleton

/**
 * A test SQS Service. Tests can connect to the service at 127.0.0.1:[CLIENT_PORT]
 */
internal class DockerSqs {

  // NB(mmihic): Because the client port is embedded directly into the queue URLs, we have to use
  // the same external port as we do for the internal port
  private val clientPort = ExposedPort.tcp(CLIENT_PORT)
  private val composer = Composer("e-sqs", Container {
    withImage("pafortin/goaws:1.0.3")
        .withName("sqs")
        .withCmd(listOf("goaws"))
        .withExposedPorts(clientPort)
        .withPortBindings(Ports().apply { bind(clientPort, Ports.Binding.bindPort(CLIENT_PORT)) })
  })

  fun start() {
    composer.start()
  }

  fun stop() {
    composer.stop()
  }


  @Singleton
  internal class Service(val sqs: DockerSqs = DockerSqs()) : AbstractIdleService() {
    override fun startUp() {
      sqs.start()
    }

    override fun shutDown() {
      sqs.stop()
    }
  }

  companion object {

    const val CLIENT_PORT = 4100
  }
}

fun main(args: Array<String>) {
  DockerSqs().start()
}
