package misk.policy.opa

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Binds
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HealthCheck
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.async.ResultCallbackTemplate
import com.github.dockerjava.core.command.PullImageResultCallback
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import com.google.common.util.concurrent.AbstractIdleService
import okio.Buffer
import wisp.logging.getLogger
import java.io.File
import javax.inject.Singleton

@Singleton
class LocalOpaService(
  private val policyPath: String = DEFAULT_POLICY_DIRECTORY
) : AbstractIdleService() {
  private var containerId: String = ""
  private val dockerClient: DockerClient = DockerClientBuilder.getInstance()
    .withDockerCmdExecFactory(NettyDockerCmdExecFactory())
    .build()

  companion object {
    const val DEFAULT_POLICY_DIRECTORY = "service/src/policy"
    const val OPA_DOCKER_IMAGE = "openpolicyagent/opa:latest-debug"
    const val OPA_CONTAINER_NAME = "opa_development"
    const val OPA_EXPOSED_PORT = 8181

    private val logger = getLogger<LocalOpaService>()
  }

  override fun startUp() {
    val policyDir = File(policyPath).absolutePath

    // Pull the image to the local docker registry.
    dockerClient.pullImageCmd(OPA_DOCKER_IMAGE).exec(PullImageResultCallback())
      .awaitCompletion()
    // Remove any stale test container.
    dockerClient.listContainersCmd()
      .withNameFilter(listOf(OPA_CONTAINER_NAME))
      .withShowAll(true)
      .exec()
      .forEach { container ->
        dockerClient.removeContainerCmd(container.id).withForce(true).exec()
      }

    // Create a new test container.
    containerId = dockerClient.createContainerCmd(OPA_DOCKER_IMAGE)
      .withCmd(listOf("run", "-b", "-s", "/repo"))
      .withHostConfig(
        HostConfig.newHostConfig()
          .withBinds(Binds(Bind(policyDir, Volume("/repo"))))
          .withPortBindings(
            PortBinding(Ports.Binding.bindPort(OPA_EXPOSED_PORT), ExposedPort.tcp(OPA_EXPOSED_PORT))
          )
      )
      .withExposedPorts(ExposedPort.tcp(OPA_EXPOSED_PORT))
      .withName(OPA_CONTAINER_NAME)
      .withTty(true)
      .exec().id

    // Start the container and let it run.
    dockerClient.startContainerCmd(containerId)
      .withContainerId(containerId)
      .exec()

    // Attach an okio backed buffer to dump the container logs.
    dockerClient.logContainerCmd(containerId)
      .withSince(0)
      .withStdErr(true)
      .withStdOut(true)
      .withFollowStream(true)
      .exec(Callback())
      .awaitStarted()
  }

  override fun shutDown() {
    dockerClient.removeContainerCmd(containerId).withForce(true).exec()
  }

  class Callback : ResultCallbackTemplate<Callback, Frame>() {
    private val buffer: Buffer = Buffer()

    override fun onNext(item: Frame) {
      buffer.write(item.payload)
      logger.info(buffer.readUtf8())
    }
  }
}
