package misk.testing

import com.google.common.util.concurrent.AbstractIdleService
import misk.ServiceManagerModule
import misk.ServiceModule
import misk.inject.KAbstractModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest(startService = true)
class NestedTestsTest {
  @Inject lateinit var store: Bookstore
  @Inject lateinit var boringService: BoringService

  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      install(ServiceManagerModule())
      install(ServiceModule<BoringService>())
    }
  }

  @Test
  fun `injected fields are stateless across tests 1`() {
    assertThat(store.count()).isEqualTo("Bookstore 1")
    // The BoringService is stateless across tests AND always starts before the test method.
    assertThat(boringService.localStartUps).isOne()
    assertThat(startUps - shutDowns).isOne()
  }

  @Test
  fun `injected fields are stateless across tests 2`() {
    assertThat(store.count()).isEqualTo("Bookstore 1")
    // The BoringService is stateless across tests AND always starts before the test method.
    assertThat(boringService.localStartUps).isOne()
    assertThat(startUps - shutDowns).isOne()
  }

  @Nested
  inner class FirstNested {

    @Test
    fun `nested can access to injected fields`() {
      assertThat(store.count()).isEqualTo("Bookstore 1")
      // The BoringService is stateless across tests AND always starts before the test method.
      assertThat(boringService.localStartUps).isOne()
      assertThat(startUps - shutDowns).isOne()
    }

    @Nested
    inner class SecondNested {
      @Test
      fun `multiple nested levels access injected fields`() {
        assertThat(store.count()).isEqualTo("Bookstore 1")
        // The BoringService is stateless across tests AND always starts before the test method.
        assertThat(boringService.localStartUps).isOne()
        assertThat(startUps - shutDowns).isOne()
      }
    }
  }

  @Singleton
  class Bookstore @Inject constructor() {
    private var counter = 0

    fun count() = "Bookstore ${++counter}"
  }

  @Singleton
  class BoringService @Inject constructor() : AbstractIdleService() {
    var localStartUps = 0

    override fun startUp() {
      localStartUps++
      startUps++
    }

    override fun shutDown() {
      shutDowns++
    }
  }

  companion object {
    var startUps = 0
    var shutDowns = 0
  }
}
