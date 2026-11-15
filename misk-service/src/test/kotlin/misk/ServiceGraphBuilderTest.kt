package misk

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.Service
import com.google.inject.Key
import com.google.inject.name.Named
import com.google.inject.name.Names
import kotlin.test.assertFailsWith
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ServiceGraphBuilderTest {
  private val keyA = key("Service A")
  private val keyB = key("Service B")
  private val keyC = key("Service C")
  private val keyD = key("Service D")
  private val keyE = key("Service E")
  private val keyF = key("Service F")
  private val keyG = key("Service G")
  private val unregistered = key("Unregistered Service")
  private val enhancementA = key("Enhancement A")

  @Test
  fun happyPathNoEnhancements() {
    val script =
      startUpAndShutDown(listOf(keyA, keyB, keyC)) {
        addDependency(dependsOn = keyA, dependent = keyC)
        addDependency(dependsOn = keyB, dependent = keyC)
      }

    assertThat(script)
      .isEqualTo(
        """
        |starting Service A
        |starting Service B
        |starting Service C
        |healthy
        |stopping Service C
        |stopping Service A
        |stopping Service B
        |"""
          .trimMargin()
      )
  }

  @Test
  fun enhancementsAndDependencies() {
    val script =
      startUpAndShutDown(listOf(keyA, keyC, enhancementA)) {
        enhanceService(toBeEnhanced = keyA, enhancement = enhancementA)
        addDependency(dependsOn = keyA, dependent = keyC)
      }

    assertThat(script)
      .isEqualTo(
        """
        |starting Service A
        |starting Service C
        |starting Enhancement A
        |healthy
        |stopping Service C
        |stopping Enhancement A
        |stopping Service A
        |"""
          .trimMargin()
      )
  }

  @Test
  fun chainsOfDependencies() {
    val script =
      startUpAndShutDown(listOf(keyA, keyB, keyC, keyD, keyE)) {
        addDependency(dependsOn = keyC, dependent = keyB)
        addDependency(dependsOn = keyA, dependent = keyC)
        addDependency(dependsOn = keyB, dependent = keyD)
        addDependency(dependsOn = keyD, dependent = keyE)
      }

    assertThat(script)
      .isEqualTo(
        """
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
        |"""
          .trimMargin()
      )
  }

  @Test
  fun treesOfDependencies() {
    val script =
      startUpAndShutDown(listOf(keyA, keyB, keyC, keyD, keyE)) {
        addDependency(dependsOn = keyB, dependent = keyC)
        addDependency(dependsOn = keyA, dependent = keyC)
        addDependency(dependsOn = keyC, dependent = keyD)
        addDependency(dependsOn = keyE, dependent = keyD)
        addDependency(dependsOn = keyA, dependent = keyE)
        addDependency(dependsOn = keyC, dependent = keyE)
      }

    assertThat(script)
      .isEqualTo(
        """
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
        |"""
          .trimMargin()
      )
  }

  @Test
  fun unsatisfiedDependency() {
    val failure =
      buildAndExpectFailure(listOf(keyA, keyC)) {
        addDependency(dependsOn = keyA, dependent = keyC)
        addDependency(dependsOn = unregistered, dependent = keyC) // Unregistered doesn't exist.
      }
    assertThat(failure)
      .hasMessage("Service C [NEW] requires $unregistered but no such service was " + "registered with the builder")
  }

  @Test
  fun failuresPropagate() {
    val bomb =
      object : AbstractService() {
        override fun doStart() = throw Exception("boom!")

        override fun doStop() = Unit

        override fun toString() = "FailingService"
      }

    assertFailsWith<IllegalStateException> {
      startUpAndShutDown(listOf(keyB)) {
        addService(keyA, bomb)
        addDependency(dependsOn = keyA, dependent = keyB)
      }
    }
  }

  @Test
  fun noServices() {
    // Note that creating a ServiceManager without registering any services will result in a warning
    // from ServiceManager.
    val script = startUpAndShutDown(listOf()) {}
    assertThat(script)
      .isEqualTo(
        """
        |healthy
        |"""
          .trimMargin()
      )
  }

  @Test
  fun transitiveEnhancements() {
    val script =
      startUpAndShutDown(listOf(keyA, keyB, keyC, keyD)) {
        enhanceService(toBeEnhanced = keyA, enhancement = keyB)
        enhanceService(toBeEnhanced = keyB, enhancement = keyC)
        addDependency(dependsOn = keyA, dependent = keyD)
        addDependency(dependsOn = keyC, dependent = keyD)
      }
    assertThat(script)
      .isEqualTo(
        """
        |starting Service A
        |starting Service B
        |starting Service C
        |starting Service D
        |healthy
        |stopping Service D
        |stopping Service C
        |stopping Service B
        |stopping Service A
        |"""
          .trimMargin()
      )
  }

  @Test
  fun enhancementDependency() {
    val script =
      startUpAndShutDown(listOf(keyA, keyB, keyC)) {
        enhanceService(toBeEnhanced = keyA, enhancement = keyB)
        addDependency(dependsOn = keyB, dependent = keyC)
      }
    assertThat(script)
      .isEqualTo(
        """
        |starting Service A
        |starting Service B
        |starting Service C
        |healthy
        |stopping Service C
        |stopping Service B
        |stopping Service A
        |"""
          .trimMargin()
      )
  }

  @Test
  fun transitiveEnhancementDependency() {
    val script =
      startUpAndShutDown(listOf(keyA, keyB, keyC, keyD)) {
        enhanceService(toBeEnhanced = keyA, enhancement = keyB)
        enhanceService(toBeEnhanced = keyB, enhancement = keyC)
        addDependency(dependsOn = keyC, dependent = keyD)
      }
    assertThat(script)
      .isEqualTo(
        """
        |starting Service A
        |starting Service B
        |starting Service C
        |starting Service D
        |healthy
        |stopping Service D
        |stopping Service C
        |stopping Service B
        |stopping Service A
        |"""
          .trimMargin()
      )
  }

  @Test
  fun enhancementMultipleTargets() {
    val script =
      startUpAndShutDown(listOf(keyA, keyB, keyC)) {
        enhanceService(toBeEnhanced = keyA, enhancement = keyB)
        enhanceService(toBeEnhanced = keyC, enhancement = keyB)
      }

    assertThat(script)
      .isEqualTo(
        """
        |starting Service A
        |starting Service C
        |starting Service B
        |healthy
        |stopping Service B
        |stopping Service A
        |stopping Service C
        |"""
          .trimMargin()
      )
  }

  @Test
  fun dependingServiceHasEnhancements() {
    val script =
      startUpAndShutDown(listOf(keyA, keyB, keyC)) {
        addDependency(dependsOn = keyA, dependent = keyB)
        enhanceService(toBeEnhanced = keyB, enhancement = keyC)
      }
    assertThat(script)
      .isEqualTo(
        """
        |starting Service A
        |starting Service B
        |starting Service C
        |healthy
        |stopping Service C
        |stopping Service B
        |stopping Service A
        |"""
          .trimMargin()
      )
  }

  @Test
  fun multipleEnhancements() {
    val script =
      startUpAndShutDown(listOf(keyA, keyB, keyC, keyD)) {
        enhanceService(toBeEnhanced = keyA, enhancement = keyB)
        enhanceService(toBeEnhanced = keyA, enhancement = keyC)
        addDependency(dependsOn = keyC, dependent = keyD)
      }
    assertThat(script)
      .isEqualTo(
        """
        |starting Service A
        |starting Service B
        |starting Service C
        |starting Service D
        |healthy
        |stopping Service B
        |stopping Service D
        |stopping Service C
        |stopping Service A
        |"""
          .trimMargin()
      )
  }

  @Test
  fun dependencyCycle() {
    val failure =
      buildAndExpectFailure(listOf(keyA, keyB, keyC, keyD)) {
        addDependency(dependsOn = keyD, dependent = keyA)
        addDependency(dependsOn = keyC, dependent = keyB)
        addDependency(dependsOn = keyA, dependent = keyC)
        addDependency(dependsOn = keyC, dependent = keyD)
      }

    assertThat(failure)
      .hasMessage("Detected cycle: Service A [NEW] -> Service D [NEW] -> Service C [NEW] -> Service A [NEW]")
  }

  @Test
  fun simpleDependencyCycle() {
    val failure =
      buildAndExpectFailure(listOf(keyA, keyB, keyC, keyD)) {
        addDependency(dependsOn = keyA, dependent = keyB)
        addDependency(dependsOn = keyB, dependent = keyA)
      }

    assertThat(failure).hasMessage("Detected cycle: Service A [NEW] -> Service B [NEW] -> Service A [NEW]")
  }

  @Test
  fun enhancementCycle() {
    val failure =
      buildAndExpectFailure(listOf(keyA, keyB)) {
        enhanceService(toBeEnhanced = keyA, enhancement = keyB)
        enhanceService(toBeEnhanced = keyB, enhancement = keyA)
      }
    assertThat(failure).hasMessage("Detected cycle: Service A [NEW] -> Service B [NEW] -> Service A [NEW]")
  }

  @Test
  fun selfEnhancement() {
    val failure = buildAndExpectFailure(listOf(keyA)) { enhanceService(toBeEnhanced = keyA, enhancement = keyA) }
    assertThat(failure).hasMessage("Detected cycle: ${keyA.name} [NEW] -> ${keyA.name} [NEW]")
  }

  @Test
  fun enhancementDependencyCycle() {
    val failure =
      buildAndExpectFailure(listOf(keyA, keyB)) {
        enhanceService(toBeEnhanced = keyA, enhancement = keyB)
        addDependency(dependsOn = keyB, dependent = keyA)
      }
    assertThat(failure).hasMessage("Detected cycle: Service A [NEW] -> Service B [NEW] -> Service A [NEW]")
  }

  @Test
  fun cannotChangeGraphOnceRunning() {
    val target = StringBuilder()
    val builder = newBuilderWithServices(target, listOf(keyA))
    val serviceManager = builder.build()
    serviceManager.startAsync()
    val badDependency = CoordinatedService { AppendingService(target, "bad dependency") }

    serviceManager.awaitHealthy()

    // This loop is probably the only sane way to obtain services from a ServiceManager?
    for (service in serviceManager.servicesByState().values()) {
      assertFailsWith<IllegalStateException> { (service as CoordinatedService).addDependentServices(badDependency) }
    }

    serviceManager.stopAsync()

    assertThat(target.toString())
      .isEqualTo(
        """
        |starting ${keyA.name}
        |stopping ${keyA.name}
        |"""
          .trimMargin()
      )
  }

  @Test
  fun enhancementsOfEnhancements() {
    val script =
      startUpAndShutDown(listOf(keyA, keyB, keyC, keyD)) {
        enhanceService(toBeEnhanced = keyA, enhancement = keyB)
        enhanceService(toBeEnhanced = keyB, enhancement = keyC)
        addDependency(dependsOn = keyA, dependent = keyD)
      }

    assertThat(script)
      .isEqualTo(
        """
        |starting Service A
        |starting Service B
        |starting Service C
        |starting Service D
        |healthy
        |stopping Service C
        |stopping Service B
        |stopping Service D
        |stopping Service A
        |"""
          .trimMargin()
      )
  }

  @Test
  fun canPrettyPrintTheGraphForDebugging() {
    val graph =
      ServiceGraphBuilder()
        .apply {
          addService(keyA, AppendingService(StringBuilder(), keyA.name))
          addService(keyB, AppendingService(StringBuilder(), keyB.name))
          addService(keyC, AppendingService(StringBuilder(), keyC.name))
          addService(keyD, AppendingService(StringBuilder(), keyD.name))
          addService(keyE, AppendingService(StringBuilder(), keyE.name))
          addService(keyF, AppendingService(StringBuilder(), keyF.name))
          addService(keyG, AppendingService(StringBuilder(), keyG.name))
          addDependency(keyA, keyB)
          addDependency(keyB, keyC)
          addDependency(keyB, keyD)
          addDependency(keyD, keyE)
          addDependency(keyA, keyF)
        }
        .toString()

    assertThat(graph)
      .isEqualTo(
        """
        |@com.google.inject.name.Named("Service A") misk.ServiceGraphBuilderTest.AppendingService
        |    |__ @com.google.inject.name.Named("Service B") misk.ServiceGraphBuilderTest.AppendingService
        |    |   |__ @com.google.inject.name.Named("Service C") misk.ServiceGraphBuilderTest.AppendingService
        |    |   \__ @com.google.inject.name.Named("Service D") misk.ServiceGraphBuilderTest.AppendingService
        |    |       \__ @com.google.inject.name.Named("Service E") misk.ServiceGraphBuilderTest.AppendingService
        |    \__ @com.google.inject.name.Named("Service F") misk.ServiceGraphBuilderTest.AppendingService
        |@com.google.inject.name.Named("Service G") misk.ServiceGraphBuilderTest.AppendingService
        |"""
          .trimMargin()
      )
  }

  @Test
  fun `debug graph shows all dependencies, including repeated subtrees`() {
    val graph =
      ServiceGraphBuilder()
        .apply {
          addService(keyA, AppendingService(StringBuilder(), keyA.name))
          addService(keyB, AppendingService(StringBuilder(), keyB.name))
          addService(keyC, AppendingService(StringBuilder(), keyC.name))
          addService(keyD, AppendingService(StringBuilder(), keyD.name))
          addDependency(keyA, keyB)
          addDependency(keyA, keyD)
          addDependency(keyB, keyC)
          addDependency(keyD, keyC)
        }
        .toString()

    assertThat(graph)
      .isEqualTo(
        """
        |@com.google.inject.name.Named("Service A") misk.ServiceGraphBuilderTest.AppendingService
        |    |__ @com.google.inject.name.Named("Service B") misk.ServiceGraphBuilderTest.AppendingService
        |    |   \__ @com.google.inject.name.Named("Service C") misk.ServiceGraphBuilderTest.AppendingService
        |    \__ @com.google.inject.name.Named("Service D") misk.ServiceGraphBuilderTest.AppendingService
        |        \__ @com.google.inject.name.Named("Service C") misk.ServiceGraphBuilderTest.AppendingService
        |"""
          .trimMargin()
      )
  }

  /**
   * Build a service graph with the named services. Configure the graph edges in [block], start the services, and return
   * the order of startup and shutdown.
   */
  private fun startUpAndShutDown(
    services: List<Key<*>>,
    block: ServiceGraphBuilder.(target: StringBuilder) -> Unit,
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
    block: ServiceGraphBuilder.(target: StringBuilder) -> Unit,
  ): IllegalStateException {
    val target = StringBuilder()
    val builder = newBuilderWithServices(target, services)
    return assertFailsWith {
      builder.block(target)
      builder.build()
    }
  }

  private fun newBuilderWithServices(target: StringBuilder, services: List<Key<*>>): ServiceGraphBuilder {
    val builder = ServiceGraphBuilder()
    for (service in services) {
      builder.addService(service, AppendingService(target, service.name))
    }
    return builder
  }

  /** AppendingService is a [Service] that appends start up and shut down events to [target]. */
  class AppendingService(private val target: StringBuilder, val name: String) : AbstractService() {

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

  private val Key<*>.name: String
    get() = (this.annotation as Named).value
}
