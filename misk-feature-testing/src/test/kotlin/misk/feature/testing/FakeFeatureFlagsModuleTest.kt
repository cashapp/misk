package misk.feature.testing

import com.google.inject.Guice
import misk.feature.Feature
import misk.feature.FeatureFlags
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FakeFeatureFlagsModuleTest {
  @Test
  fun testModule() {
    val injector = Guice.createInjector(FakeFeatureFlagsModule().withOverrides {
      override(Feature("foo"), 24)
    })

    val flags = injector.getInstance(FeatureFlags::class.java)
    assertEquals(24, flags.getInt(Feature("foo"), ""))
  }
}