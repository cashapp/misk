package misk.feature.testing

import com.google.inject.Guice
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.inject.KAbstractModule
import misk.moshi.MoshiAdapterModule
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FakeFeatureFlagsModuleTest {
  @Test
  fun testModule() {
    val injector = Guice.createInjector(FakeFeatureFlagsModule().withOverrides {
      override(Feature("foo"), 24)
    }, object : KAbstractModule() {
      override fun configure() {
        // Misk services automatically get this binding, but no need to depend on all of misk to
        // test this.
        bind<Moshi>().toInstance(Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build())
      }
    })

    val flags = injector.getInstance(FeatureFlags::class.java)
    assertEquals(24, flags.getInt(Feature("foo"), "bar"))
  }
}
