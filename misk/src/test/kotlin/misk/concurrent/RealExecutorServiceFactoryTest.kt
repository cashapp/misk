package misk.concurrent

import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import kotlin.test.assertFailsWith

internal class RealExecutorServiceFactoryTest {
  @Test fun happyPath() {
    val log = mutableListOf<String>()
    val factory = RealExecutorServiceFactory(FakeClock())
    val executorService = factory.named("happy-%d").single()

    executorService.execute {
      log += "running ${Thread.currentThread().name} run 1"
    }

    factory.startAsync().awaitRunning()

    executorService.execute {
      log += "running ${Thread.currentThread().name} run 2"
    }

    factory.stopAsync().awaitTerminated()
    assertThat(executorService.isTerminated).isTrue()

    assertThat(log).containsExactly(
        "running happy-0 run 1",
        "running happy-0 run 2"
    )
  }

  @Test fun cannotSubmitAfterShutdown() {
    val factory = RealExecutorServiceFactory(FakeClock())
    val executorService = factory.single()
    factory.startAsync().awaitRunning()
    factory.stopAsync().awaitTerminated()

    assertFailsWith<RejectedExecutionException> {
      executorService.execute {
      }
    }
  }

  @Test fun cannotCreateExecutorServicesAfterShutdown() {
    val factory = RealExecutorServiceFactory(FakeClock())
    factory.startAsync().awaitRunning()
    factory.stopAsync().awaitTerminated()

    assertFailsWith<IllegalStateException> {
      factory.single()
    }
  }

  @Test fun executorDoesNotShutDownPromptly() {
    val latch = CountDownLatch(1)
    val factory = RealExecutorServiceFactory(FakeClock())
    val executorService = factory.named("happy-%d").single()

    // This will keep the executor service from shutting down...
    executorService.execute {
      latch.await()
    }

    factory.startAsync().awaitRunning()

    factory.doStop(timeout = Duration.ofMillis(100L))

    val exception = assertFailsWith<IllegalStateException> {
      factory.stopAsync().awaitTerminated()
    }

    assertThat(exception.cause).hasMessageContaining("took longer than PT0.1S to terminate")

    // ...let the executor service finally shut down.
    latch.countDown()
  }
}
