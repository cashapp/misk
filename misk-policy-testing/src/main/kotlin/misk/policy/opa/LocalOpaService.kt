package misk.policy.opa

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Binds
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.core.async.ResultCallbackTemplate
import com.github.dockerjava.core.command.PullImageResultCallback
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.google.common.util.concurrent.AbstractIdleService
import misk.backoff.ExponentialBackoff
import misk.backoff.retry
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import wisp.logging.getLogger
import java.io.File
import java.io.IOException
import java.time.Duration

class LocalOpaService(
  private val policyPath: String,
  private val withLogging: Boolean
) : AbstractIdleService() {
  private var containerId: String = ""
  private val defaultDockerClientConfig =
    DefaultDockerClientConfig.createDefaultConfigBuilder().build()
  private val httpClient = ApacheDockerHttpClient.Builder()
    .dockerHost(defaultDockerClientConfig.dockerHost)
    .sslConfig(defaultDockerClientConfig.sslConfig)
    .maxConnections(100)
    .connectionTimeout(Duration.ofSeconds(60))
    .responseTimeout(Duration.ofSeconds(120))
    .build()
  private val dockerClient: DockerClient =
    DockerClientImpl.getInstance(defaultDockerClientConfig, httpClient)

  companion object {
    const val DEFAULT_POLICY_DIRECTORY = "service/src/policy"
    const val OPA_DOCKER_IMAGE = "openpolicyagent/opa:latest-debug"
    const val OPA_CONTAINER_NAME = "opa_development"
    const val OPA_EXPOSED_PORT = 8181

    private val logger = getLogger<LocalOpaService>()
  }

  override fun startUp() {
    val policyDir = File(policyPath).absolutePath

    try {
      dockerClient.pingCmd().exec()
    } catch (e: Exception) {
      throw IllegalStateException("Couldn't connect to Docker daemon", e)
    }

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
      .withCmd(listOf("run", "-b", "-s", "-w", "/repo"))
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
      .exec()

    // Attach an okio backed buffer to dump the container logs.
    if (withLogging) {
      dockerClient.logContainerCmd(containerId)
        .withSince(0)
        .withStdErr(true)
        .withStdOut(true)
        .withFollowStream(true)
        .exec(Callback())
        .awaitStarted()
    }
    waitUntilHealthy()
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

  private fun waitUntilHealthy() {
    if (!dockerClient.inspectContainerCmd(containerId).exec().state.running!!) {
      throw Exception("OPA is not running")
    }
    try {
      retry(
        5, ExponentialBackoff(
        Duration.ofSeconds(1),
        Duration.ofSeconds(5)
      )
      )
      {
        val client = OkHttpClient()
        val request = Request.Builder()
          .url("http://localhost:$OPA_EXPOSED_PORT/health")
          .build()

        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) throw IOException("OPA is not healthy")
        }
      }
    } catch (e: Exception) {
      throw e
    }
  }
}
