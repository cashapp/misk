package misk.cloud.gcp.spanner

import com.google.cloud.spanner.Spanner
import com.google.inject.util.Modules
import misk.environment.DeploymentModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING
import javax.inject.Inject

@MiskTest()
class GoogleSpannerModuleTest {
  val spannerConfig = SpannerConfig(
    project_id = "test-project",
    instance_id = "test-instance",
    database = "test-database",
    emulator = SpannerEmulatorConfig(
      enabled = true
    )
  )

  @MiskTestModule
  val module = Modules.combine(
    DeploymentModule(TESTING),
    GoogleSpannerModule(spannerConfig),
  )

  @Inject lateinit var spanner: Spanner

  @Test
  fun `it creates a Spanner client and emulator to develop with`() {
    assertTrue(spanner.instanceAdminClient
      .getInstance(spannerConfig.instance_id)
      .getDatabase(spannerConfig.database)
      .exists()
    )
  }
}
