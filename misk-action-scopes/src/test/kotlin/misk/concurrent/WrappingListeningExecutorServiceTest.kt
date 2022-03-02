package misk.concurrent

import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class WrappingListeningExecutorServiceTest {

  private val wrapCounter = AtomicInteger()
  private val executor = object : WrappingListeningExecutorService() {
    val wrapped = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun <T> wrap(callable: Callable<T>): Callable<T> {
      return Callable {
        wrapCounter.incrementAndGet()
        callable.call()
      }
    }

    override fun delegate(): ListeningExecutorService = wrapped
  }

  @BeforeEach
  fun clearWrapped() {
    wrapCounter.set(0)
  }

  @Test
  fun submit() {
    val result = executor.submit(Callable { "foo" }).get()
    assertThat(result).isEqualTo("foo")
    assertThat(wrapCounter.get()).isEqualTo(1)
  }

  @Test
  fun submitRunnable() {
    val executed = AtomicBoolean()
    executor.submit({ executed.set(true) }).get()
    assertThat(executed.get()).isTrue()
    assertThat(wrapCounter.get()).isEqualTo(1)
  }

  @Test
  fun submitWithResult() {
    val executed = AtomicBoolean()
    val result = executor.submit({ executed.set(true) }, "foo").get()
    assertThat(result).isEqualTo("foo")
    assertThat(executed.get()).isTrue()
    assertThat(wrapCounter.get()).isEqualTo(1)
  }

  @Test
  fun invokeAll() {
    val results = executor.invokeAll(
      listOf(
        Callable { "foo" },
        Callable { "bar" },
        Callable { "zed" }
      )
    ).map { it.get() }

    assertThat(results).containsExactly("foo", "bar", "zed")
    assertThat(wrapCounter.get()).isEqualTo(3)
  }

  @Test
  fun execute() {
    val latch = CountDownLatch(1)
    executor.execute({ latch.countDown() })
    latch.await()
    assertThat(wrapCounter.get()).isEqualTo(1)
  }
}
