package misk

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import com.google.inject.Key
import com.google.inject.Provider
import com.google.inject.name.Names
import misk.ServiceGraphBuilderTest.AppendingService
import misk.inject.toKey
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

class CoordinatedServiceTest {

  @RepeatedTest(100) fun fuzzCoordinatedServiceGraphStartAndStop() {
    val services = List(10) { service("$it") }.shuffled()

    // Build randomized service graph and dependency chain
    val manager = ServiceGraphBuilder().also { builder ->
      services.forEach { builder.addService(it) }
      val randomKeys = services.shuffled().map { it.first }.toMutableList()
      val dependencies = mutableListOf<Key<*>>()
      while (randomKeys.isNotEmpty()) {
        val next = randomKeys.removeFirst()
        if (dependencies.isNotEmpty()) {
          builder.addDependency(dependent = dependencies.random(), dependsOn = next)
        }
        dependencies += next
      }
    }.build()

    manager.startAsync()
    manager.awaitHealthy()

    assertThat(services).allMatch { (_, service) -> service.state() == Service.State.RUNNING }

    manager.stopAsync()
    manager.awaitStopped()

    assertThat(services).allMatch { (_, service) -> service.state() == Service.State.TERMINATED }
  }

  @Test fun canShutDownGracefullyOnStartFailure() {
    class FailOnStartService : AbstractIdleService() {
      override fun startUp() = error("failed to start")
      override fun shutDown() {}
    }

    val canStartService = service("canStartService")
    val neverStartedService = service("neverStartedService")
    val failOnStartService = (key("failOnStartService") to FailOnStartService())
      .toCoordinated()

    val manager = ServiceGraphBuilder().also { builder ->
      builder.addService(canStartService)
      builder.addService(failOnStartService)
      builder.addService(neverStartedService)
      builder.addDependency(failOnStartService.first, canStartService.first)
      builder.addDependency(neverStartedService.first, failOnStartService.first)
    }.build()

    manager.startAsync()
    assertThatThrownBy {
      manager.awaitHealthy()
    }.isInstanceOf(IllegalStateException::class.java)

    manager.stopAsync()
    manager.awaitStopped(5, TimeUnit.SECONDS)

    assertThat(canStartService.second.state()).isEqualTo(Service.State.TERMINATED)
    assertThat(failOnStartService.second.state()).isEqualTo(Service.State.FAILED)
    assertThat(neverStartedService.second.state()).isEqualTo(Service.State.TERMINATED)
  }

  @Test fun canShutDownGracefullyOnStopFailure() {
    class FailOnStopService : AbstractIdleService() {
      override fun startUp() {}
      override fun shutDown() = error("failed to stop")
    }

    val service = service("service")
    val failOnStopService =
      (key("failOnStopService") to FailOnStopService()).toCoordinated()

    val manager = ServiceGraphBuilder().also { builder ->
      builder.addService(failOnStopService)
      builder.addService(service)
      builder.addDependency(failOnStopService.first, service.first)
    }.build()

    manager.startAsync()
    manager.awaitHealthy()

    manager.stopAsync()
    manager.awaitStopped()

    assertThat(service.second.state()).isEqualTo(Service.State.TERMINATED)
    assertThat(failOnStopService.second.state()).isEqualTo(Service.State.FAILED)
  }

  @Test fun cannotAddRunningServiceAsDependency() {
    val target = StringBuilder()
    val runningService = CoordinatedService {
      AppendingService(target, "I will be running")
    }
    val newService = CoordinatedService {
      AppendingService(target, "I will not run")
    }


    runningService.startAsync()

    assertFailsWith<IllegalStateException> {
      newService.addDependentServices(runningService)
    }
    assertFailsWith<IllegalStateException> {
      newService.addDependentServices(runningService)
    }

    runningService.stopAsync()
  }

  @Test fun cannotWrapRunningService() {
    val target = StringBuilder()
    val service = AppendingService(target, "Running Service")
    service.startAsync()

    val failure = assertFailsWith<IllegalStateException> {
      CoordinatedService{ service }.service.startAsync().awaitRunning()
    }

    assertThat(failure).hasMessage("Running Service must be NEW for it to be coordinated")

    service.stopAsync()
  }

  private class FakeService(
    val name: String
  ) : AbstractIdleService() {
    override fun startUp() {}
    override fun shutDown() {}
    override fun toString() = "FakeService-$name"
  }

  private fun service(id: String): Pair<Key<*>, CoordinatedService> =
    (key(id) to FakeService(id)).toCoordinated()

  private fun ServiceGraphBuilder.addService(pair: Pair<Key<*>, Service>) {
    addService(pair.first, pair.second)
  }

  private fun Pair<Key<*>, Service>.toCoordinated() =
    this.first to CoordinatedService(
      this.first,
      Provider { this.second }
    )

  private fun key(name: String) = Key.get(Service::class.java, Names.named(name))
}
