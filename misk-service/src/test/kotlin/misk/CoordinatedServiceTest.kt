package misk

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import com.google.inject.Key
import com.google.inject.Provider
import com.google.inject.name.Names
import misk.ServiceGraphBuilderTest.AppendingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertFailsWith

class CoordinatedServiceTest {

  @RepeatedTest(100) fun fuzzCoordinatedServiceGraphStartAndStop() {
    fun service(id: String): Pair<Key<*>, Service> = key(id) to FakeService(id).toCoordinated()

    val services = List(10) { service("$it") }.shuffled()

    // Build randomized service graph and dependency chain
    val manager = ServiceGraphBuilder().also { builder ->
      services.forEach { (key, service) ->
        builder.addService(key, service)
      }
      val randomKeys = services.shuffled().map { it.first }.toMutableList()
      val dependencies = mutableListOf<Key<*>>()
      while(randomKeys.isNotEmpty()) {
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

  @Test fun cannotAddRunningServiceAsDependency() {
    val target = StringBuilder()
    val runningService = CoordinatedService(
      Provider<Service> {
        AppendingService(target, "I will be running")
      }
    )
    val newService = CoordinatedService(
      Provider<Service> {
        AppendingService(target, "I will not run")
      }
    )

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
      CoordinatedService(Provider<Service> { service }).startAsync().awaitRunning()
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

  private fun Service.toCoordinated() = CoordinatedService(Provider { this })

  private fun key(name: String) = Key.get(Service::class.java, Names.named(name))
}
