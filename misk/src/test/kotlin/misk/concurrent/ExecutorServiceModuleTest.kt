package misk.concurrent

import com.google.inject.Injector
import com.google.inject.Key
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import javax.inject.Inject
import javax.inject.Qualifier

@MiskTest
internal class ExecutorServiceModuleTest {
  @MiskTestModule val module = TestModule()

  @Inject lateinit var injector: Injector
  @Inject @Fixed lateinit var fixed: ExecutorService
  @Inject @Unbound lateinit var unbound: ExecutorService

  @Test
  internal fun fixed() {
    assertThat((fixed as ThreadPoolExecutor).maximumPoolSize).isEqualTo(2)
  }

  @Test
  internal fun unbound() {
    assertThat((unbound as ThreadPoolExecutor).maximumPoolSize).isEqualTo(Int.MAX_VALUE)
  }

  @Test
  internal fun `getting the executor twice returns the same instance`() {
    val key = Key.get(ExecutorService::class.java, Fixed::class.java)
    val executorService = injector.getInstance(key)
    assertThat(executorService).isSameAs(injector.getInstance(key))
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(ExecutorServiceModule.withFixedThreadPool(Fixed::class, "fixed-%d", 2))
      install(ExecutorServiceModule.withUnboundThreadPool(Unbound::class, "unbound-%d"))
    }
  }

  @Qualifier
  @Target(AnnotationTarget.FIELD)
  @Retention(AnnotationRetention.RUNTIME)
  annotation class Fixed

  @Qualifier
  @Target(AnnotationTarget.FIELD)
  @Retention(AnnotationRetention.RUNTIME)
  annotation class Unbound
}
