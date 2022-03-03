package misk.feature.testing

import com.google.inject.Guice
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.feature.getJson
import misk.feature.testing.FakeFeatureFlagsTest.JsonFeature
import misk.inject.KAbstractModule
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FakeFeatureFlagsModuleTest {
  @Test
  fun `uses overrides from FakeFeatureFlagsOverrideModule`() {
    val injector = Guice.createInjector(
      FakeFeatureFlagsModule(),
      MoshiTestingModule(),
      object : KAbstractModule() {
        override fun configure() {
          install(FakeFeatureFlagsOverrideModule { override(Feature("foo"), 24) })
          install(FakeFeatureFlagsOverrideModule {
            overrideJson(
              Feature("jsonFeature"),
              JsonFeature("testValue")
            )
          })
        }
      }
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

  @Test
  fun `withOverrides method and FakeFeatureFlagsOverrideModule both work`() {
    val injector = Guice.createInjector(
      FakeFeatureFlagsModule().withOverrides {
        overrideJson(Feature("jsonFeature"), JsonFeature("testValue"))
      },
      MoshiTestingModule(),
      object : KAbstractModule() {
        override fun configure() {
          install(FakeFeatureFlagsOverrideModule { override(Feature("foo"), 24) })
        }
      }
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

  @Test
  fun `deprecated withOverrides method`() {
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
