package misk.cloud.gcp.spanner

import com.google.cloud.spanner.Spanner
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import wisp.containers.ContainerUtil
import wisp.deployment.TESTING
import javax.inject.Inject

@MiskTest(startService = true)
class GoogleSpannerModuleTest {
  val spannerConfig = SpannerConfig(
    project_id = "test-project",
    instance_id = "test-instance",
    database = "test-database",
    emulator = SpannerEmulatorConfig(
      enabled = true,
      hostname = ContainerUtil.dockerTargetOrLocalHost()
    )
  )

  @MiskTestModule
  val module = Modules.combine(
    MiskTestingServiceModule(),
    DeploymentModule(TESTING),
    GoogleSpannerEmulatorModule(spannerConfig),
    GoogleSpannerModule(spannerConfig),
  )

  @Inject lateinit var spanner: Spanner
  @Inject lateinit var spannerService: GoogleSpannerService

  @Order(1)
  @Test
  fun `it creates a Spanner client and emulator to develop with`() {
    assertTrue(
      spanner.instanceAdminClient
        .getInstance(spannerConfig.instance_id)
        .getDatabase(spannerConfig.database)
        .exists()
    )
  }

  @Nested
  inner class `GoogleSpannerService tests` {
    @Order(2)
    @Test
    fun `it binds an open Spanner client when started`() {
      // Since startService=true is specified, this should already be running.
      assertTrue(spannerService.isRunning)

      // The client should be callable for requests.
      assertFalse(spanner.isClosed)
    }

    @Order(3)
    @Test
    fun `it shuts down the Spanner client when stopped`() {
      spannerService.stopAsync()
      spannerService.awaitTerminated()
      assertTrue(spanner.isClosed)
    }
  }
}
