package misk.inject

import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class KInstallOnceModuleTest {
  @MiskTestModule
  val module : KAbstractModule = TestModule()

  @Inject private lateinit var someValue : String

  @Test fun testInstallOnceModule() {
    assertThat(someValue).isEqualTo("Install once test")
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(TestInstallOnceModule())
      install(TestInstallOnceModule())
    }
  }

  class TestInstallOnceModule : KInstallOnceModule() {
    override fun configure() {
      bind<String>().toInstance("Install once test")
    }
  }
}
