package misk.inject

import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import jakarta.inject.Inject
import jakarta.inject.Qualifier

@MiskTest
class KInstallOnceModuleTest {
  @MiskTestModule
  val module : KAbstractModule = TestModule()

  @Inject @MyMap lateinit var map : Map<String, String>

  @Test fun testInstallOnceModule() {
    assertThat(map["key"]).isEqualTo("value")
    assertThat(map.size).isEqualTo(1)
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(TestInstallOnceModule())
      install(TestInstallOnceModule())
    }
  }

  class TestInstallOnceModule : KInstallOnceModule() {
    override fun configure() {
      val binder = newMapBinder<String, String>(MyMap::class)
      binder.addBinding("key").toInstance("value")
    }
  }
}

@Qualifier
annotation class MyMap
