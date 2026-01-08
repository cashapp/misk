package misk.inject

import com.google.inject.CreationException
import com.google.inject.Guice
import jakarta.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest
class KInstallOnceModuleTest {
  @MiskTestModule @Suppress("unused") val module = TestModule()

  @Inject private lateinit var map: Map<String, TestValue>

  @Test
  fun testInstallOnceModule() {
    assertThat(map["key"]).isEqualTo(TestValue("abc"))
    assertThat(map.size).isEqualTo(1)
  }

  @Test
  fun failure() {
    val exception = assertFailsWith<CreationException> { Guice.createInjector(TestFailureModule()) }
    assertEquals(
      """
      |com.google.inject.CreationException: Unable to create injector, see the following errors:
      |
      |1) [Guice/DuplicateMapKey]: Duplicate key "key" found in Map<String, KInstallOnceModuleTest${"$"}TestValue>.
      |
      |Duplicates:
      |  Key: "key"
      |  Bound at:
      |    1 : KInstallOnceModuleTest${"$"}TestDuplicateFailureModule.configure(KInstallOnceModuleTest.kt:88)
      |      \_ installed by: KInstallOnceModuleTest${"$"}TestFailureModule -> KInstallOnceModuleTest${"$"}TestDuplicateFailureModule
      |    2 : KInstallOnceModuleTest${"$"}TestDuplicateFailureModule.configure(KInstallOnceModuleTest.kt:88)
      |      \_ installed by: KInstallOnceModuleTest${"$"}TestFailureModule -> KInstallOnceModuleTest${"$"}TestDuplicateFailureModule
      |
      |MapBinder declared at:
      |  KInstallOnceModuleTest${"$"}TestDuplicateFailureModule.configure(KInstallOnceModuleTest.kt:97)
      |      \_ installed by: KInstallOnceModuleTest${"$"}TestFailureModule -> KInstallOnceModuleTest${"$"}TestDuplicateFailureModule -> RealMapBinder
      |
      |1 error
      |
      |======================
      |Full classname legend:
      |======================
      |KInstallOnceModuleTest${"$"}TestDuplicateFailureModule: "misk.inject.KInstallOnceModuleTest${"$"}TestDuplicateFailureModule"
      |KInstallOnceModuleTest${"$"}TestFailureModule:          "misk.inject.KInstallOnceModuleTest${"$"}TestFailureModule"
      |KInstallOnceModuleTest${"$"}TestValue:                  "misk.inject.KInstallOnceModuleTest${"$"}TestValue"
      |RealMapBinder:                                     "com.google.inject.internal.RealMapBinder"
      |========================
      |End of classname legend:
      |========================
      |"""
        .trimMargin(),
      exception.toString(),
    )
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

  class TestFailureModule : KAbstractModule() {
    override fun configure() {
      install(TestDuplicateFailureModule("abc"))
      install(TestDuplicateFailureModule("123"))
    }
  }

  class TestDuplicateFailureModule(val token: String) : KAbstractModule() {
    override fun configure() {
      val binder = newMapBinder<String, TestValue>()
      binder.addBinding("key").toInstance(TestValue(token))
    }
  }

  private data class TestValue(val value: String)
}
