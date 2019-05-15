package misk

import misk.ServiceGraphBuilderTest.AppendingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.IllegalStateException
import kotlin.test.assertFailsWith


class CoordinatedService2Test {
  @Test fun cannotAddRunningServiceAsDependency() {
    val target = StringBuilder()
    val runningService = CoordinatedService2(AppendingService(target, "I will be running"))
    val newService = CoordinatedService2(AppendingService(target, "I will not run"))

    runningService.startAsync()

    assertFailsWith<IllegalStateException> {
      newService.addDependencies(runningService)
    }
    assertFailsWith<IllegalStateException> {
      newService.addDependencies(runningService)
    }

    runningService.stopAsync()
  }

  @Test fun cannotWrapRunningService() {
    val target = StringBuilder()
    val service = AppendingService(target, "Running Service")
    service.startAsync()

    val failure = assertFailsWith<IllegalStateException> { CoordinatedService2(service) }
    assertThat(failure).hasMessage("Running Service must be NEW for it to be coordinated")

    service.stopAsync()
  }

}