package misk.inject

import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class InstallOnceTest {
  @MiskTestModule
  val module = TestInstallModule()

  @Inject @TestAnnotation private lateinit var installedOnce: String

  @Test
  fun testInstallOnce() {
    Assertions.assertThat(installedOnce).startsWith("only one of me with identity")
  }
}

class TestInstallModule : KAbstractModule() {
  override fun configure() {
    // Install many. It should still resolve.
    install(OnceModule())
    install(OnceModule())
    install(OnceModule())
    install(OnceModule())
    install(OnceModule())
    install(OnceModule())
    install(OnceModule())
  }
}

class OnceModule : KInstallOnceModule() {
  override fun configure() {
    bind(String::class.java).annotatedWith(TestAnnotation::class.java)
        .toInstance("only one of me with identity ${System.identityHashCode(this)}")
  }
}