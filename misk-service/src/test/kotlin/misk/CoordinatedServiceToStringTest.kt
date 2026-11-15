package misk

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Provides
import com.google.inject.util.Modules
import jakarta.inject.Inject
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import misk.inject.KAbstractModule
import misk.logging.getLogger
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest
class CoordinatedServiceToStringTest {
  @Retention(AnnotationRetention.RUNTIME) @Qualifier internal annotation class TestAnnotation

  @MiskTestModule
  val module =
    Modules.combine(
      MiskTestingServiceModule(),
      ServiceModule<TestCoordinatedService>(),
      ServiceModule<TestCoordinatedService>(TestAnnotation::class),
      AnnotatedInstanceModule(),
    )

  @Inject lateinit var serviceManager: ServiceManager

  @Test
  fun toStringDoesntInjectInstance() {
    val coordinatedTestCoordinatedService =
      serviceManager.servicesByState().get(Service.State.NEW).find { it.toString() == "TestCoordinatedService [NEW]" }

    val coordinatedTestCoordinatedServiceAnnotated =
      serviceManager.servicesByState().get(Service.State.NEW).find {
        it.toString() == "@TestAnnotation TestCoordinatedService [NEW]"
      }

    assertThat(coordinatedTestCoordinatedService).isNotNull
    assertThat(coordinatedTestCoordinatedService is CoordinatedService)
    assertThat(coordinatedTestCoordinatedService.toString()).isEqualTo("TestCoordinatedService [NEW]")

    assertThat(coordinatedTestCoordinatedServiceAnnotated).isNotNull
    assertThat(coordinatedTestCoordinatedServiceAnnotated is CoordinatedService)
    assertThat(coordinatedTestCoordinatedServiceAnnotated.toString())
      .isEqualTo("@TestAnnotation TestCoordinatedService [NEW]")

    assertThat(TestCoordinatedService.instances).isEqualTo(0)

    serviceManager.startAsync()
    serviceManager.awaitHealthy()

    assertThat(TestCoordinatedService.instances).isEqualTo(2)
    assertThat(coordinatedTestCoordinatedService.toString()).isEqualTo("[1] TestCoordinatedService [RUNNING]")
    assertThat(coordinatedTestCoordinatedServiceAnnotated.toString()).isEqualTo("[2] TestCoordinatedService [RUNNING]")

    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    assertThat(coordinatedTestCoordinatedService.toString()).isEqualTo("[1] TestCoordinatedService [TERMINATED]")
    assertThat(coordinatedTestCoordinatedServiceAnnotated.toString())
      .isEqualTo("[2] TestCoordinatedService [TERMINATED]")
  }

  @Singleton
  internal class TestCoordinatedService @Inject constructor() : AbstractIdleService() {
    private val id: Int

    init {
      logger.info { "Created" }
      id = ++instances
    }

    override fun startUp() {
      logger.info { "Started" }
    }

    override fun shutDown() {
      logger.info { "Shutdown" }
    }

    override fun toString(): String = "[$id] ${this::class.simpleName} [${state()}]"

    companion object {
      private val logger = getLogger<TestCoordinatedService>()
      var instances = 0
    }
  }

  internal class AnnotatedInstanceModule : KAbstractModule() {
    @Provides
    @TestAnnotation
    @Singleton
    fun providesAnnotatedInstance(): TestCoordinatedService {
      return TestCoordinatedService()
    }
  }
}
