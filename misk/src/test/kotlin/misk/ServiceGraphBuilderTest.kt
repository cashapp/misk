package misk

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.Service
import com.google.inject.Key
import com.google.inject.name.Named
import com.google.inject.name.Names
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ServiceGraphBuilderTest {
  private val keyA = key("Service A")
  private val keyB = key("Service B")
  private val keyC = key("Service C")
  private val keyD = key("Service D")
  private val keyE = key("Service E")
  private val unregistered = key("Unregistered Service")
  private val enhancementA = key("Enhancement A")

  @Test
  fun happyPathNoEnhancements() {
    val script = startUpAndShutDown(listOf(keyA, keyB, keyC)) {
      addDependency(service = keyA, dependency = keyC)
      addDependency(service = keyB, dependency = keyC)
    }

    assertThat(script).isEqualTo("""
        |starting Service A
        |starting Service B
        |starting Service C
        |healthy
        |stopping Service C
        |stopping Service A
        |stopping Service B
        |""".trimMargin())
  }

  @Test
  fun enhancementsAndDependencies() {
    val script = startUpAndShutDown(listOf(keyA, keyC, enhancementA)) { _ ->
      enhanceService(service = keyA, enhancement = enhancementA)
      addDependency(service = keyA, dependency = keyC)
    }

    assertThat(script).isEqualTo("""
        |starting Service A
        |starting Enhancement A
        |starting Service C
        |healthy
        |stopping Service C
        |stopping Enhancement A
        |stopping Service A
        |""".trimMargin())
  }

  @Test fun chainsOfDependencies() {
    val script = startUpAndShutDown(listOf(keyA, keyB, keyC, keyD, keyE)) {
      addDependency(service = keyC, dependency = keyB)
      addDependency(service = keyA, dependency = keyC)
      addDependency(service = keyB, dependency = keyD)
      addDependency(service = keyD, dependency = keyE)
    }

    assertThat(script).isEqualTo("""
        |starting Service A
        |starting Service C
        |starting Service B
        |starting Service D
        |starting Service E
        |healthy
        |stopping Service E
        |stopping Service D
        |stopping Service B
        |stopping Service C
        |stopping Service A
        |""".trimMargin())
  }

  @Test fun treesOfDependencies() {
    val script = startUpAndShutDown(listOf(keyA, keyB, keyC, keyD, keyE)) {
      addDependency(service = keyB, dependency = keyC)
      addDependency(service = keyA, dependency = keyC)
      addDependency(service = keyC, dependency = keyD)
      addDependency(service = keyE, dependency = keyD)
      addDependency(service = keyA, dependency = keyE)
      addDependency(service = keyC, dependency = keyE)
    }

    assertThat(script).isEqualTo("""
        |starting Service A
        |starting Service B
        |starting Service C
        |starting Service E
        |starting Service D
        |healthy
        |stopping Service D
        |stopping Service E
        |stopping Service C
        |stopping Service A
        |stopping Service B
        |""".trimMargin())
  }

  @Test fun unsatisfiedDependency() {
    val failure = buildAndExpectFailure(listOf(keyA, keyC)) {
      addDependency(service = keyA, dependency = keyC)
      addDependency(service = unregistered, dependency = keyC) // Unregistered doesn't exist.
    }
    assertThat(failure).hasMessage("Service C requires $unregistered but no such service was "
        + "registered with the builder")
  }

  @Test fun failuresPropagate() {
    val bomb = object : AbstractService() {
      override fun doStart() = throw Exception("boom!")
      override fun doStop() = Unit
      override fun toString() = "FailingService"
    }

    assertFailsWith<IllegalStateException> {
      startUpAndShutDown(listOf(keyB)) {
        addService(keyA, bomb)
        addDependency(service = keyA, dependency = keyB)
      }
    }
  }

  @Test fun noServices() {
    // Note that creating a ServiceManager without registering any services will result in a warning
    // from ServiceManager.
    val script = startUpAndShutDown(listOf()) {}
    assertThat(script).isEqualTo("""
        |healthy
        |""".trimMargin())
  }

  @Test fun transitiveEnhancements() {
    val script = startUpAndShutDown(listOf(keyA, keyB, keyC, keyD)) {
      enhanceService(service = keyA, enhancement = keyB)
      enhanceService(service = keyB, enhancement = keyC)
      addDependency(service = keyA, dependency = keyD)
    }
    assertThat(script).isEqualTo("""
        |starting Service A
        |starting Service B
        |starting Service C
        |starting Service D
        |healthy
        |stopping Service D
        |stopping Service C
        |stopping Service B
        |stopping Service A
        |""".trimMargin())
  }

  @Test fun enhancementDependency() {
    val script = startUpAndShutDown(listOf(keyA, keyB, keyC)) {
      enhanceService(service = keyA, enhancement = keyB)
      addDependency(service = keyB, dependency = keyC)
    }
    assertThat(script).isEqualTo("""
        |starting Service A
        |starting Service B
        |starting Service C
        |healthy
        |stopping Service C
        |stopping Service B
        |stopping Service A
        |""".trimMargin())
  }

  @Test fun transitiveEnhancementDependency() {
    val script = startUpAndShutDown(listOf(keyA, keyB, keyC, keyD)) {
      enhanceService(service = keyA, enhancement = keyB)
      enhanceService(service = keyB, enhancement = keyC)
      addDependency(service = keyC, dependency = keyD)
    }
    assertThat(script).isEqualTo("""
        |starting Service A
        |starting Service B
        |starting Service C
        |starting Service D
        |healthy
        |stopping Service D
        |stopping Service C
        |stopping Service B
        |stopping Service A
        |""".trimMargin())
  }

  @Test fun enhancementCannotHaveMultipleTargets() {
    val failure = buildAndExpectFailure(listOf(keyA, keyB, keyC)) {
      enhanceService(service = keyA, enhancement = keyB)
      enhanceService(service = keyC, enhancement = keyB)
    }
    assertThat(failure).hasMessage("Enhancement $keyB cannot be applied more than once")
  }

  @Test fun dependingServiceHasEnhancements() {
    val script = startUpAndShutDown(listOf(keyA, keyB, keyC)) {
      addDependency(service = keyA, dependency = keyB)
      enhanceService(service = keyB, enhancement = keyC)
    }
    assertThat(script).isEqualTo("""
        |starting Service A
        |starting Service B
        |starting Service C
        |healthy
        |stopping Service C
        |stopping Service B
        |stopping Service A
        |""".trimMargin())
  }

  @Test fun multipleEnhancements() {
    val script = startUpAndShutDown(listOf(keyA, keyB, keyC, keyD)) {
      enhanceService(service = keyA, enhancement = keyB)
      enhanceService(service = keyA, enhancement = keyC)
      addDependency(service = keyC, dependency = keyD)
    }
    assertThat(script).isEqualTo("""
        |starting Service A
        |starting Service B
        |starting Service C
        |starting Service D
        |healthy
        |stopping Service B
        |stopping Service D
        |stopping Service C
        |stopping Service A
        |""".trimMargin())
  }

  @Test fun dependencyCycle() {
    val failure = buildAndExpectFailure(listOf(keyA, keyB, keyC, keyD)) {
      addDependency(service = keyD, dependency = keyA)
      addDependency(service = keyC, dependency = keyB)
      addDependency(service = keyA, dependency = keyC)
      addDependency(service = keyC, dependency = keyD)
    }

    assertThat(failure)
        .hasMessage("Detected cycle: Service A -> Service D -> Service C -> Service A")
  }

  @Test fun simpleDependencyCycle() {
    val failure = buildAndExpectFailure(listOf(keyA, keyB, keyC, keyD)) {
      addDependency(service = keyA, dependency = keyB)
      addDependency(service = keyB, dependency = keyA)
    }

    assertThat(failure).hasMessage("Detected cycle: Service A -> Service B -> Service A")
  }

  @Test fun enhancementCycle() {
    val failure = buildAndExpectFailure(listOf(keyA, keyB)) {
      enhanceService(service = keyA, enhancement = keyB)
      enhanceService(service = keyB, enhancement = keyA)
    }
    assertThat(failure).hasMessage("Detected cycle: Service A -> Service B -> Service A")
  }

  @Test fun selfEnhancement() {
    val failure = buildAndExpectFailure(listOf(keyA)) {
      enhanceService(service = keyA, enhancement = keyA)
    }
    assertThat(failure).hasMessage("Detected cycle: ${keyA.name} -> ${keyA.name}")
  }

  @Test fun enhancementDependencyCycle() {
    val failure = buildAndExpectFailure(listOf(keyA, keyB)) {
      enhanceService(service = keyA, enhancement = keyB)
      addDependency(service = keyB, dependency = keyA)
    }
    assertThat(failure).hasMessage("Detected cycle: Service A -> Service B -> Service A")
  }

  /**
   * Build a service graph with the named services. Configure the graph edges in [block], start the
   * services, and return the order of startup and shutdown.
   */
  private fun startUpAndShutDown(
    services: List<Key<*>>,
    block: ServiceGraphBuilder.(target: StringBuilder) -> Unit
  ): String {
    val target = StringBuilder()
    val builder = newBuilderWithServices(target, services)
    builder.block(target)
    val serviceManager = builder.build()

    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    target.append("healthy\n")
    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    return target.toString()
  }

  private fun buildAndExpectFailure(
    services: List<Key<*>>,
    block: ServiceGraphBuilder.(target: StringBuilder) -> Unit
  ): IllegalStateException {
    val target = StringBuilder()
    val builder = newBuilderWithServices(target, services)
    return assertFailsWith {
      builder.block(target)
      builder.build()
    }
  }

  private fun newBuilderWithServices(
    target: StringBuilder,
    services: List<Key<*>>
  ): ServiceGraphBuilder {
    val builder = ServiceGraphBuilder()
    for (service in services) {
      builder.addService(service, AppendingService(target, service.name))
    }
    return builder
  }

  /** AppendingService is a [Service] that appends start up and shut down events to [target]. */
  class AppendingService(
    private val target: StringBuilder,
    val name: String
  ) : AbstractService() {

    override fun doStart() {
      target.append("starting $name\n")
      notifyStarted()
    }

    override fun doStop() {
      target.append("stopping $name\n")
      notifyStopped()
    }

    override fun toString() = name
  }

  /** A `Key<Service>` named [name]. */
  private fun key(name: String) = Key.get(Service::class.java, Names.named(name))

  val Key<*>.name: String
    get() = (this.annotation as Named).value
}