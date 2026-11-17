package misk.testing

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Injector
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.lang.ref.WeakReference
import misk.ServiceManagerModule
import misk.ServiceModule
import misk.inject.ReusableTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

// These classes run from the `injectorReuseLruTest` task, in @Order, with injector reuse on and an LRU size of 1.

private var firstInjector: WeakReference<Injector>? = null

@MiskTest(startService = true)
@Order(1)
class ReusedInjectorEvictionSetupTest {
  @MiskTestModule val module = FirstModule()

  @Inject lateinit var injector: Injector

  @Test
  fun `records the first injector`() {
    firstInjector = WeakReference(injector)
  }

  class FirstModule : ReusableTestModule() {
    override fun configure() {
      install(ServiceManagerModule())
      install(ServiceModule<IdleService>())
    }
  }
}

@MiskTest(startService = true)
@Order(2)
class ReusedInjectorEvictionTest {
  @MiskTestModule val module = SecondModule()

  @Test
  fun `an evicted injector can be garbage collected`() {
    for (attempt in 1..20) {
      System.gc()
      if (firstInjector!!.get() == null) break
      Thread.sleep(100)
    }
    assertThat(firstInjector!!.get()).isNull()
  }

  class SecondModule : ReusableTestModule() {
    override fun configure() {
      install(ServiceManagerModule())
      install(ServiceModule<IdleService>())
    }
  }
}

@Singleton
class IdleService @Inject constructor() : AbstractIdleService() {
  override fun startUp() {}

  override fun shutDown() {}
}
