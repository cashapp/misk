package misk.containers

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.async.ResultCallbackTemplate
import com.github.dockerjava.core.command.PullImageResultCallback
import com.github.dockerjava.core.command.WaitContainerResultCallback
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import misk.logging.getLogger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A [Container] creates a Docker container for testing.
 *
 * Tests provide a lambda to build a [CreateContainerCmd]. The [createCmd] lambda must set
 * [CreateContainerCmd.withName] and [CreateContainerCmd.withImage]. All other fields are
 * optional. The [Composer] takes care of setting up the network.
 *
 * There may be a need to configure your container between the creation and start steps.
 * [beforeStartHook] provides you with an id to your container allowing you to
 * manipulate as necessary before the command/entrypoint is invoked.
 *
 * See [Composer] for an example.
 */
data class Container(
  val createCmd: CreateContainerCmd.() -> Unit,
  val beforeStartHook: (docker: DockerClient, id: String) -> Unit
) {
  constructor(createCmd: CreateContainerCmd.() -> Unit) : this(createCmd, { _, _ -> })
}

/**
 * [Composer] composes many [Container]s together to use in a unit test.
 *
 * The [Container]s are networked using a dedicated Docker network. Tests need to expose ports
 * in order for the test to communicate with the containers over 127.0.0.1.
 *
 * The following example composes Kafka and Zookeeper containers for testing. Kafka is exposed
 * to the jUnit test via 127.0.0.1:9102. In this example, Zookeeper is not exposed to the test.
 *
 * ```
 *     val zkContainer = Container {
 *       this
 *         .withImage("confluentinc/cp-zookeeper")
 *         .withName("zookeeper")
 *         .withEnv("ZOOKEEPER_CLIENT_PORT=2181")
 *     }
 *     val kafka = Container {
 *       this
 *         .withImage("confluentinc/cp-kafka"
 *         .withName("kafka")
 *         .withExposedPorts(ExposedPort.tcp(port))
 *         .withPortBindings(Ports().apply {
 *           bind(ExposedPort.tcp(9102), Ports.Binding.bindPort(9102))
 *         })
 *         .withEnv(
 *           "KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181",
 *           "KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9102")
 *         }
 *     val composer = Composer("e-kafka", zkContainer, kafka)
 *     composer.start()
 * ```
 */
class Composer(private val name: String, private vararg val containers: Container) {

  private val network = DockerNetwork(
    "$name-net",
    docker
  )
  private val containerIds = mutableMapOf<String, String>()
  private val running = AtomicBoolean(false)

  fun start() {
    if (!running.compareAndSet(false, true)) return
    Runtime.getRuntime().addShutdownHook(Thread { stop() })

    network.start()

    for (container in containers) {
      val name = container.name()
      val create = docker.createContainerCmd("todo").apply(container.createCmd)
      require(create.image != "todo") {
        "must provide an image for container ${create.name}"
      }

      docker.listContainersCmd()
        .withShowAll(true)
        .withLabelFilter(mapOf("name" to name))
        .exec()
        .forEach {
          log.info { "removing previous $name container with id ${it.id}" }
          docker.removeContainerCmd(it.id).exec()
        }

      log.info { "pulling ${create.image} for $name container" }

      val imageParts = create.image!!.split(":")
      docker.pullImageCmd(imageParts[0])
        .withTag(imageParts.getOrElse(1) { "latest" })
        .exec(PullImageResultCallback()).awaitCompletion()

      log.info { "starting $name container" }

      val id = create
        .withNetworkMode(network.id())
        .withLabels(mapOf("name" to name))
        .withTty(true)
        .exec()
        .id
      containerIds[name] = id

      container.beforeStartHook(docker, id)

      docker.startContainerCmd(id).exec()
      docker.logContainerCmd(id)
        .withStdErr(true)
        .withStdOut(true)
        .withFollowStream(true)
        .withSince(0)
        .exec(LogContainerResultCallback())
        .awaitStarted()

      log.info { "started $name; container id=$id" }
    }
  }

  private fun Container.name(): String {
    val create = docker.createContainerCmd("todo").apply(createCmd)
    require(!create.name.isNullOrBlank()) {
      "must provide a name for the container"
    }
    return "$name/${create.name}"
  }

  fun stop() {
    if (!running.compareAndSet(true, false)) return

    for (container in containers) {
      val name = container.name()
      val id = containerIds[name]!!
      log.info { "killing $name with container id $id" }
      docker.removeContainerCmd(id).withForce(true).exec()

      try {
        log.info { "waiting for $name to terminate" }
        docker.waitContainerCmd(id).exec(
          GracefulWaitContainerResultCallback()
        ).awaitCompletion()
      } catch (th: Throwable) {
        log.error(th) { "could not kill $name with container id $id" }
      }

      log.info { "killed $name with container id $id" }
    }

    network.stop()
  }

  private class LogContainerResultCallback :
    ResultCallbackTemplate<LogContainerResultCallback, Frame>() {
    override fun onNext(item: Frame) {
      String(item.payload).trim().split('\r', '\n').filter { it.isNotBlank() }.forEach {
        log.info(it)
      }
    }
  }

  private class GracefulWaitContainerResultCallback : WaitContainerResultCallback() {
    override fun onError(throwable: Throwable?) {
      // this is ok, just meant that the container already terminated before we tried to wait
      if (throwable is NotFoundException) {
        return
      }
      super.onError(throwable)
    }
  }

  private companion object {
    private val log = getLogger<Composer>()
    private val docker: DockerClient = DockerClientBuilder.getInstance()
      .withDockerCmdExecFactory(NettyDockerCmdExecFactory())
      .build()
  }
}

private class DockerNetwork(private val name: String, private val docker: DockerClient) {

  private lateinit var networkId: String

  fun id(): String {
    return networkId
  }

  fun start() {
    log.info { "creating $name network" }

    docker.listNetworksCmd().withNameFilter(name).exec().forEach {
      log.info { "removing previous $name network with id ${it.id}" }
      docker.removeNetworkCmd(it.id).exec()
    }
    networkId = docker.createNetworkCmd()
      .withName(name)
      .withCheckDuplicate(true)
      .exec()
      .id
  }

  fun stop() {
    log.info { "removing $name network with id $networkId" }
    docker.removeNetworkCmd(networkId).exec()
  }

  companion object {
    private val log = getLogger<DockerNetwork>()
  }
}
