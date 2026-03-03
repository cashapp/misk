package misk.feature.testing

import com.google.inject.Guice
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.feature.getJson
import misk.feature.testing.FakeFeatureFlagsTest.JsonFeature
import misk.inject.KAbstractModule
import misk.moshi.MoshiAdapterModule
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FakeFeatureFlagsModuleTest {
  @Test
  fun testModule() {
    val injector = Guice.createInjector(FakeFeatureFlagsModule().withOverrides {
      override(Feature("foo"), 24)
      overrideJson(Feature("jsonFeature"), JsonFeature("testValue"))
    }, MoshiTestingModule())

    val flags = injector.getInstance(FeatureFlags::class.java)
    assertEquals(24, flags.getInt(Feature("foo"), "bar"))
    assertEquals("testValue", flags.getJson<JsonFeature>(Feature("jsonFeature"), "key").value)
  }
}
