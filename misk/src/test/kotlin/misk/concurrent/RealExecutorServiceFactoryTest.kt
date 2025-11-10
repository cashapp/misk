package misk.concurrent

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.opentracing.mock.MockTracer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import misk.time.FakeClock
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import kotlin.test.assertFailsWith

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

  @Test fun fixedWithMetricsCreatesExecutorWithMetrics() {
    val meterRegistry = SimpleMeterRegistry()
    val factory = RealExecutorServiceFactory(FakeClock())
    val log = mutableListOf<String>()
    
    val executorService = factory.fixedWithMetrics(
      nameFormat = "metrics-test-%d",
      threadCount = 2,
      meterRegistry = meterRegistry,
      metricPrefix = "executor",
      executorServiceName = "test-executor"
    )

    factory.startAsync().awaitRunning()

    executorService.execute {
      log += "task 1"
    }
    executorService.execute {
      log += "task 2"
    }

    factory.stopAsync().awaitTerminated()

    assertThat(log).containsExactlyInAnyOrder("task 1", "task 2")
    assertThat(executorService.isTerminated).isTrue()
    
    val meters = meterRegistry.meters.filter { it.id.name.startsWith("executor") }
    assertThat(meters).isNotEmpty

    val executorNameTags = meters.flatMap { it.id.tags }
      .filter { it.key == "executor_name" }
    assertThat(executorNameTags).isNotEmpty
    assertThat(executorNameTags.first().value).isEqualTo("test-executor")
  }

  @Test fun fixedWithMetricsWithTracingEnabled() {
    val tracer = MockTracer()
    val meterRegistry = SimpleMeterRegistry()
    val log = mutableListOf<String>()
    val factory = RealExecutorServiceFactory(FakeClock())
    factory.tracer = tracer
    
    val executorService = factory.fixedWithMetrics(
      nameFormat = "traced-metrics-%d",
      threadCount = 2,
      meterRegistry = meterRegistry,
      metricPrefix = "traced.executor",
      executorServiceName = "traced-executor"
    )

    factory.startAsync().awaitRunning()

    executorService.execute {
      log += "traced task"
    }

    factory.stopAsync().awaitTerminated()

    assertThat(log).containsExactly("traced task")
    assertThat(tracer.finishedSpans()).hasSize(1)
    assertThat(tracer.finishedSpans().first().operationName()).isEqualTo("execute")
    
    val meters = meterRegistry.meters.filter { it.id.name.startsWith("traced.executor") }
    assertThat(meters).isNotEmpty
  }
}
