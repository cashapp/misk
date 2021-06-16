package misk.policy.opa

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.async.ResultCallbackTemplate
import misk.backoff.DontRetryException
import misk.backoff.ExponentialBackoff
import misk.backoff.retry
import mu.KotlinLogging
import okhttp3.Request
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

fun runCommand(command: String): Int {
  OpaContainer.logger.info(command)
  return try {
    val process = ProcessBuilder("bash", "-c", command)
      .redirectOutput(ProcessBuilder.Redirect.INHERIT)
      .redirectError(ProcessBuilder.Redirect.INHERIT)
      .start()
    process.waitFor(60, TimeUnit.MINUTES)
    return process.exitValue()
  } catch (e: IOException) {
    OpaContainer.logger.warn("'$command' threw exception", e)
    -1 // Failed
  }
}

class OpaContainer(
  val docker: DockerClient
) {

  private var containerId: String? = null

  private var isRunning = false
  private var stopContainerOnExit = true
  private var startupFailure: Exception? = null

  fun start() {
    val startupFailure = this.startupFailure
    if (startupFailure != null) {
      throw startupFailure
    }
    if (isRunning) {
      return
    }

    isRunning = true
    try {
      doStart()
    } catch (e: Exception) {
      this.startupFailure = e
      throw e
    }
  }

  companion object {
    val logger = KotlinLogging.logger {}

    // Latest OPA version
    const val IMAGE = "openpolicyagent/opa:latest-debug"
    const val CONTAINER_NAME = "opa-testing"

    fun pullImage() {
      if (imagePulled.get()) {
        return
      }

      synchronized(this) {
        if (imagePulled.get()) {
          return
        }

        if (runCommand(
            "docker pull $IMAGE"
          ) != 0) {
          logger.warn("Failed to pull OPA docker image. Proceeding regardless.")
        }
        imagePulled.set(true)
      }
    }

    private val imagePulled = AtomicBoolean()
  }

  private fun pullImage() {
    OpaContainer.pullImage()
  }

  private fun doStart() {
    pullImage()

    // Kill and remove container that don't match our requirements
    var matchingContainer: Container? = null
    val runningContainer = docker.listContainersCmd()
      .withNameFilter(listOf(containerName()))
      .withLimit(1)
      .exec()
      .firstOrNull()
    if (runningContainer != null) {
      val mismatches = containerMismatches(runningContainer)
      if (!mismatches.isEmpty()) {
        logger.info {
          "container named ${runningContainer.name()} does not match our requirements, " +
            "force removing and starting a new one: ${mismatches.joinToString(", ")}"
        }
        docker.removeContainerCmd(runningContainer.id).withForce(true).exec()
      } else {
        matchingContainer = runningContainer
      }
    }

    // use the volume to seed and test policy locally
    val confVolume = Volume("/repo")
    val cmd = arrayOf("run", "--server", "-b", "/repo")
    val httpPort = ExposedPort.tcp(8181)
    val ports = Ports()
    ports.bind(httpPort, Ports.Binding.bindPort(httpPort.port))

    val resource = OpaTestingModule::class.java.classLoader.getResource("policy")!!

    val policyDir = Paths.get(resource.toURI()).toAbsolutePath().toFile().absolutePath

    containerId = matchingContainer?.id
    if (containerId == null) {
      logger.info("Starting Policy Engine")
      stopContainerOnExit = true
      containerId = docker.createContainerCmd(IMAGE)
        .withCmd(cmd.toList())
        .withHostConfig(HostConfig.newHostConfig().withBinds(Bind(policyDir, confVolume)))
//            .withVolumes(confVolume)
//            .withBinds(Bind(cluster.configDir.toAbsolutePath().toString(), confVolume))
        .withExposedPorts(httpPort)
        .withPortBindings(ports)
        .withTty(true)
        .withName(containerName())
        .exec().id!!

      val containerId = containerId!!
      docker.startContainerCmd(containerId).exec()
      docker.logContainerCmd(containerId)
        .withStdErr(true)
        .withStdOut(true)
        .withFollowStream(true)
        .withSince(0)
        .exec(LogContainerResultCallback())
        .awaitStarted()
    }
    logger.info("Started OPA with container id $containerId")

    waitUntilHealthy()
    // Maybe seed data here
  }

  private fun containerName() = CONTAINER_NAME

  /**
   * Check if the container is a container that we can use for our tests. If it is not return a
   * description of the mismatch.
   */
  private fun containerMismatches(container: Container): List<String> = listOfNotNull(
    shouldMatch("container name", container.name(), containerName()),
    shouldMatch("container state", container.state, "running"),
    shouldMatch("container image", container.image, IMAGE)
  )

  private fun shouldMatch(description: String, actual: Any, expected: Any): String? =
    if (expected != actual) {
      "$description \"${actual}\" does not match \"${expected}\""
    } else {
      null
    }

  /**
   * Return the single name of a container and strip away the prefix /
   */
  private fun Container.name(): String {
    val name = names.single()
    return if (name.startsWith("/")) name.substring(1) else name
  }

  private fun waitUntilHealthy() {
    try {
      retry(
        20, ExponentialBackoff(
        Duration.ofSeconds(1),
        Duration.ofSeconds(5)
      )
      ) {
        // Add OKHTTP call to hit localhost:8181/health anmd waiut for 200
        val build = okhttp3.OkHttpClient.Builder().build()
        val response =
          build.newCall(Request.Builder().get().url("http://localhost:8181/health").build())
            .execute()
        check(response.isSuccessful)
      }
    } catch (e: DontRetryException) {
      throw Exception(e.message)
    } catch (e: Exception) {
      throw Exception("OPA engine failed to start up in time", e)
    }
  }

  fun stop() {
    if (stopContainerOnExit) {
      logger.info("Stopping container $containerId")
      val containerId = containerId
      if (containerId != null) {
//        docker.removeContainerCmd(containerId).withForce(true).exec()
        docker.killContainerCmd(containerId)
      }
    }
  }

  class LogContainerResultCallback : ResultCallbackTemplate<LogContainerResultCallback, Frame>() {
    override fun onNext(item: Frame) {
      logger.info(String(item.payload).trim())
    }
  }
}


