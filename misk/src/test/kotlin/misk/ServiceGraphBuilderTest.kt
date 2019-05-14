package misk

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.Service
import com.google.inject.Key
import com.google.inject.name.Names
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ServiceGraphBuilderTest {
  val a = NamedKey("Service A")
  val b = NamedKey("Service B")
  val c = NamedKey("Service C")
  val d = NamedKey("Service D")
  val e = NamedKey("Service E")
  val unregistered = NamedKey("Unregistered Service")
  val enhancementA = NamedKey("Enhancement A")

  @Test
  fun happyPathNoEnhancements() {
    val script = startUpAndShutDown(listOf(a, b, c)) {
      addDependency(c.key, a.key)
      addDependency(c.key, b.key)
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

    val script = startUpAndShutDown(listOf(a, c, enhancementA)) { _ ->
      addEnhancement(a.key, enhancementA.key)
      addDependency(c.key, a.key)
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
    val script = startUpAndShutDown(listOf(a, b, c, d, e)) {
      addDependency(b.key, c.key)
      addDependency(c.key, a.key)
      addDependency(d.key, b.key)
      addDependency(e.key, d.key)
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
    val script = startUpAndShutDown(listOf(a, b, c, d, e)) {
      addDependency(c.key, b.key)
      addDependency(c.key, a.key)
      addDependency(d.key, c.key)
      addDependency(d.key, e.key)
      addDependency(e.key, a.key)
      addDependency(e.key, c.key)
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
    val failure = buildAndExpectFailure(listOf(a, c)) {
      addDependency(c.key, a.key)
      addDependency(c.key, unregistered.key) // unregistered doesn't exist
    }
    assertThat(failure).hasMessage("Service C requires ${unregistered.key} but no such service was "
        + "registered with the builder")
  }

  @Test fun failuresPropagate() {
    val bomb = object : AbstractService() {
      override fun doStart() = throw Exception("boom!")
      override fun doStop() = Unit
      override fun toString() = "FailingService"
    }

    assertFailsWith<IllegalStateException> {
      startUpAndShutDown(listOf(b)) {
        addService(a.key, bomb)
        addDependency(b.key, a.key)
      }
    }
  }

  @Test fun emptyBuilder() {
    val failure = buildAndExpectFailure(null) {}
    assertThat(failure).hasMessage("ServiceGraphBuilder cannot be built without registering "
        + "services")
  }

  @Test fun transitiveEnhancements() {
    /*
    A
      enhanced by B
                    enhanced by C
      depended on by D
     -> should be A, B, C, D
     */
    val script = startUpAndShutDown(listOf(a, b, c, d)) {
      addEnhancement(a.key, b.key)
      addEnhancement(b.key, c.key)
      addDependency(d.key, a.key)
    }
    // not sure about this! my expectation might be wrong, or the actual might also be correct!
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
    /*
    A
      enhanced by B
                    depended on by C
     -> should be A, B, C
     */
    val script = startUpAndShutDown(listOf(a, b, c)) {
      addEnhancement(a.key, b.key)
      addDependency(c.key, b.key)
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
    /*
    A
      enhanced by B
                    enhanced by C
                                  depended on by D
     -> should be A, B, C, D
     */
    val script = startUpAndShutDown(listOf(a, b, c, d)) {
      addEnhancement(a.key, b.key)
      addEnhancement(b.key, c.key)
      addDependency(d.key, c.key)
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
    /*
    A
      enhanced by B
    C
      enhanced by B
     -> BOOM
     */
    val failure = buildAndExpectFailure(listOf(a, b, c)) {
      addEnhancement(a.key, b.key)
      addEnhancement(c.key, b.key)
    }
    assertThat(failure).hasMessage("Enhancement ${b.key} cannot be applied more than once")
  }

  @Test fun dependingServiceHasEnhancements() {
    /*
    A
      depended on by B
                       enhanced by C
     -> A, B, C
     */
    val script = startUpAndShutDown(listOf(a, b, c)) {
      addDependency(b.key, a.key)
      addEnhancement(b.key, c.key)
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
    /*
    A
      enhanced by B
      enhanced by C
                    depended on by D
     -> A, B, C, D
     */
    val script = startUpAndShutDown(listOf(a, b, c, d)) {
      addEnhancement(a.key, b.key)
      addEnhancement(a.key, c.key)
      addDependency(d.key, c.key)
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
    val failure = buildAndExpectFailure(listOf(a, b, c, d)) {
      addDependency(a.key, d.key)
      addDependency(b.key, c.key)
      addDependency(c.key, a.key)
      addDependency(d.key, c.key)
    }

    assertThat(failure).hasMessage("Detected cycle: Service A -> Service D -> Service C -> "
        + "Service A")
  }

  @Test fun simpleDependencyCycle() {
    val failure = buildAndExpectFailure(listOf(a, b, c, d)) {
      addDependency(b.key, a.key)
      addDependency(a.key, b.key)
    }

    assertThat(failure).hasMessage("Detected cycle: Service A -> Service B -> Service A")
  }

  @Test fun enhancementCycle() {
    /*
    A
      enhanced by B
                    enhanced by A
     -> BOOM
     */
    val failure = buildAndExpectFailure(listOf(a, b)) {
      addEnhancement(a.key, b.key)
      addEnhancement(b.key, a.key)
    }
    assertThat(failure).hasMessage("Detected cycle: Service A -> Service B -> Service A")
  }

  @Test fun selfEnhancement() {
    /*
    A
      enhanced by A
     -> BOOM
     */
    val failure = buildAndExpectFailure(listOf(a)) {
      addEnhancement(a.key, a.key)
    }
    assertThat(failure).hasMessage("Detected cycle: ${a.name} -> ${a.name}")
  }

  @Test fun enhancementDependencyCycle() {
    /*
    A
      enhanced by B
                    depended on by A
     -> BOOM
     */
    val failure = buildAndExpectFailure(listOf(a, b)) {
      addEnhancement(a.key, b.key)
      addDependency(a.key, b.key)
    }
    assertThat(failure).hasMessage("Detected cycle: Service A -> Service B -> Service A")
  }

  private fun startUpAndShutDown(
    services: List<NamedKey>?,
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
    services: List<NamedKey>?,
    block: ServiceGraphBuilder.(target: StringBuilder) -> Unit
  ): IllegalStateException {
    return assertFailsWith<IllegalStateException> {
      val target = StringBuilder()
      val builder = newBuilderWithServices(target, services)
      builder.block(target)

      builder.build() // should fail
    }
  }

  private fun newBuilderWithServices(
    target: StringBuilder,
    services: List<NamedKey>?
  ): ServiceGraphBuilder {
    val builder = ServiceGraphBuilder()
    services?.forEach { builder.addService(it.key, AppendingService(target, it.name)) }
    return builder
  }

  /**
   * AppendingService is a Service that appends messages to its `target` on start up and shut down.
   */
  class AppendingService(
    val target: StringBuilder,
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

  /**
   * Data class that pairs a Key with its name, leaving the name readable by the universe.
   */
  data class NamedKey(val name: String) {
    val key: Key<*> = Key.get(Service::class.java, Names.named(name))
  }

}