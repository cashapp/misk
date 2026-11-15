package misk.service

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class CachedTestServiceTest {
  class ServiceA : CachedTestService() {
    override fun actualStartup() {
      started.set(true)
    }

    override fun actualShutdown() {
      started.set(false)
    }

    companion object {
      var started = AtomicBoolean(false)
    }
  }

  class ServiceB : CachedTestService() {
    override fun actualStartup() {
      started.set(true)
    }

    override fun actualShutdown() {
      started.set(false)
    }

    companion object {
      var started = AtomicBoolean(false)
    }
  }

  @Test
  fun cachedTestServiceWithMultipleServices() {
    val serviceA = ServiceA()
    val serviceB = ServiceB()

    assertFalse { ServiceA.started.get() }
    assertFalse { ServiceB.started.get() }

    serviceA.startAsync().awaitRunning()
    assertTrue { ServiceA.started.get() }
    assertFalse { ServiceB.started.get() }

    serviceB.startAsync().awaitRunning()
    assertTrue { ServiceA.started.get() }
    assertTrue { ServiceB.started.get() }
  }
}
