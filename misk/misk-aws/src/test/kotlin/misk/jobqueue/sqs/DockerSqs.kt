package misk.jobqueue.sqs

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.async.ResultCallbackTemplate
import com.github.dockerjava.core.command.WaitContainerResultCallback
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import com.google.common.util.concurrent.AbstractIdleService
import misk.logging.getLogger
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Singleton

internal class DockerSqs {
  private lateinit var containerId: String
  private val running = AtomicBoolean(false)

  fun start() {
    if (!running.compareAndSet(false, true)) return

    val clientPort = ExposedPort.tcp(CLIENT_PORT)

    log.info { "starting fake SQS service" }

    val cmd = listOf("goaws")

    // NB(mmihic): Because the client port is embedded directly into the queue URLs, we have to use
    // the same external port as we do for the internal port
    containerId = docker.createContainerCmd("pafortin/goaws")
        .withCmd(cmd)
        .withExposedPorts(clientPort)
        .withPortBindings(Ports().apply { bind(clientPort, Ports.Binding.bindPort(CLIENT_PORT)) })
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

    log.info { "started fake SQS service; container id=$containerId" }
  }

  fun stop() {
    if (!running.compareAndSet(true, false)) return

    log.info { "killing SQS service with container id $containerId" }
    docker.removeContainerCmd(containerId).withForce(true).exec()

    try {
      log.info { "waiting for SQS service to terminate" }
      docker.waitContainerCmd(containerId).exec(WaitContainerResultCallback()).awaitCompletion()
    } catch (e: NotFoundException) {
      // this is ok, just meant that the container already terminated before we tried to wait
    } catch (th: Throwable) {
      log.error(th) { "could not kill SQS service with container id $containerId" }
    }

    log.info { "killed SQS service with container id $containerId" }
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

  class LogContainerResultCallback : ResultCallbackTemplate<LogContainerResultCallback, Frame>() {
    override fun onNext(item: Frame) {
      String(item.payload).trim().split('\r', '\n').filter { it.isNotBlank() }.forEach {
        log.info(it)
      }
    }
  }


  companion object {
    private val log = getLogger<DockerSqs>()
    private val docker: DockerClient = DockerClientBuilder.getInstance()
        .withDockerCmdExecFactory(NettyDockerCmdExecFactory())
        .build()

    const val CLIENT_PORT = 4100
  }
}

fun main(args: Array<String>) {
  DockerSqs().start()
}