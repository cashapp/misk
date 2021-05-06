package misk.containers

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd

/*
 * Deprecating for wisp.containers
 */

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
 *         withImage("confluentinc/cp-zookeeper")
 *         withName("zookeeper")
 *         withEnv("ZOOKEEPER_CLIENT_PORT=2181")
 *     }
 *     val kafka = Container {
 *         withImage("confluentinc/cp-kafka"
 *         withName("kafka")
 *         withExposedPorts(ExposedPort.tcp(port))
 *         withPortBindings(Ports().apply {
 *           bind(ExposedPort.tcp(9102), Ports.Binding.bindPort(9102))
 *         })
 *         withEnv(
 *           "KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181",
 *           "KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9102")
 *         }
 *     val composer = Composer("e-kafka", zkContainer, kafka)
 *     composer.start()
 * ```
 */
class Composer(private val name: String, private vararg val containers: Container) {

  private val delegate: wisp.containers.Composer = run {
    val wispContainers =
      containers.map { wisp.containers.Container(it.createCmd, it.beforeStartHook) }.toTypedArray()
    wisp.containers.Composer(name, *wispContainers)
  }

  fun start() {
    delegate.start()
  }

  fun stop() {
    delegate.stop()
  }
}
