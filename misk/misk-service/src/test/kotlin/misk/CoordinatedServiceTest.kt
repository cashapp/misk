package misk

import com.google.common.util.concurrent.Service
import com.google.inject.Provider
import misk.ServiceGraphBuilderTest.AppendingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class CoordinatedServiceTest {
  @Test fun cannotAddRunningServiceAsDependency() {
    val target = StringBuilder()
    val runningService = CoordinatedService(Provider<Service> {
      AppendingService(target, "I will be running")
    })
    val newService = CoordinatedService(Provider<Service> {
      AppendingService(target, "I will not run")
    })

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
      CoordinatedService(Provider<Service> { service })
    }
    assertThat(failure).hasMessage("Running Service must be NEW for it to be coordinated")

    service.stopAsync()
  }
}
