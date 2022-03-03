package misk.feature.testing

import com.google.inject.Guice
import com.google.inject.Key
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.feature.getJson
import misk.feature.testing.FakeFeatureFlagsTest.JsonFeature
import misk.inject.KAbstractModule
import misk.inject.keyOf
import org.junit.jupiter.api.Test
import javax.inject.Qualifier
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
  fun `uses overrides from FakeFeatureFlagsOverrideModule with annotation`() {
    val injector = Guice.createInjector(
      FakeFeatureFlagsModule(AnotherFeatureFlag::class),
      FakeFeatureFlagsModule(),
      MoshiTestingModule(),
      object : KAbstractModule() {
        override fun configure() {
          install(FakeFeatureFlagsOverrideModule { override(Feature("foo"), 24) })
          install(FakeFeatureFlagsOverrideModule(AnotherFeatureFlag::class) { override(Feature("foo"), 10101) })
        }
      }
    )

    val flags = injector.getInstance(FeatureFlags::class.java)
    val anotherFeatureFlag = injector.getInstance(Key.get(FeatureFlags::class.java, AnotherFeatureFlag::class.java))
    assertEquals(24, flags.getInt(Feature("foo"), "bar"))
    assertEquals(10101, anotherFeatureFlag.getInt(Feature("foo"), "bar"))
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

@Qualifier
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
annotation class AnotherFeatureFlag
