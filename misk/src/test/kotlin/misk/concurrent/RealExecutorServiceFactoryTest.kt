package misk.concurrent

import io.opentracing.mock.MockTracer
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import kotlin.test.assertFailsWith

@Suppress("UnstableApiUsage") // Guava's Service is @Beta.
internal class RealExecutorServiceFactoryTest {
  @Test fun happyPath() {
    val tracer = MockTracer()
    val log = mutableListOf<String>()
    val factory = RealExecutorServiceFactory(FakeClock())
    factory.tracer = tracer
    val executorService = factory.single("happy-%d")

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
    assertThat(tracer.finishedSpans().map { it.operationName() }).containsExactly(
        "execute",
        "execute"
    )
  }

  @Test fun cannotSubmitAfterShutdown() {
    val factory = RealExecutorServiceFactory(FakeClock())
    val executorService = factory.single("executor-service-factory-test-%d")
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
      factory.single("executor-service-factory-test-%d")
    }
  }

  @Test fun cannotCreateMultipleExecutorsWithTheSameName() {
    val factory = RealExecutorServiceFactory(FakeClock())
    factory.single("foo-%d")
    factory.single("bar-%d")

    val exception = assertFailsWith<IllegalStateException> {
      factory.single("foo-%d")
    }
    assertThat(exception).hasMessageContaining("multiple executor services named foo")
  }

  @Test fun executorDoesNotShutDownPromptly() {
    val latch = CountDownLatch(1)
    val factory = RealExecutorServiceFactory(FakeClock())
    val executorService = factory.single("executor-service-factory-test-%d")

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
