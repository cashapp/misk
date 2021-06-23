package misk.feature.testing

import com.google.inject.Guice
import misk.feature.testing.FakeFeatureFlagsTest.JsonFeature
import org.junit.jupiter.api.Test
import wisp.feature.Feature
import wisp.feature.FeatureFlags
import wisp.feature.getJson
import kotlin.test.assertEquals

class FakeFeatureFlagsModuleTest {
  @Test
  fun testModule() {
    val injector = Guice.createInjector(
      FakeFeatureFlagsModule().withOverrides {
        override(Feature("foo"), 24)
        overrideJson(Feature("jsonFeature"), JsonFeature("testValue"))
      },
      MoshiTestingModule()
    )

    val flags = injector.getInstance(FeatureFlags::class.java)
    assertEquals(24, flags.getInt(Feature("foo"), "bar"))
    assertEquals(
      "testValue",
      flags.getJson<JsonFeature>(
        Feature("jsonFeature"),
        "key"
      ).value
    )
  }
}
