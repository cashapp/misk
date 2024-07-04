package misk.inject

import jakarta.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest
class KInstallOnceModuleTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var map: Map<String, TestValue>

  @Test fun testInstallOnceModule() {
    assertThat(map["key"]).isEqualTo(TestValue("abc"))
    assertThat(map.size).isEqualTo(1)
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(TestInstallOnceModule("abc"))
      install(TestInstallOnceModule("123"))
    }
  }

  class TestInstallOnceModule(val token: String) : KInstallOnceModule() {
    override fun configure() {
      val binder = newMapBinder<String, TestValue>()
      binder.addBinding("key").toInstance(TestValue(token))
    }
  }

  private data class TestValue(val value: String)
}

