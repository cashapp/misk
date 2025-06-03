package misk.redis.lettuce

import io.lettuce.core.api.AsyncCloseable
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class AsyncCloseableExtensionsTest {

  class TestCloseable : AsyncCloseable {
    var isOpen: Boolean = true
      private set

    val isClosed: Boolean
      get() = !isOpen

    fun isOpenAsync(): CompletableFuture<Boolean> = CompletableFuture.completedFuture(isOpen)

    fun isOpenAsyncFailed(): CompletableFuture<Boolean> =
      CompletableFuture.failedFuture(TestCloseableException(isOpen))

    override fun closeAsync(): CompletableFuture<Void> =
      CompletableFuture.completedFuture<Void>(null)
        .whenComplete { _, _ -> isOpen = false }
  }

  class TestCloseableException(val isOpen: Boolean) : Exception("test failure")

  private fun newCloseable(): CompletableFuture<TestCloseable> =
    CompletableFuture.supplyAsync { TestCloseable() }

  @Test
  fun `test 'closeOnCompletion' on successfully completed future`() {
    var closeable: TestCloseable? = null

    val isOpen = newCloseable()
      .whenComplete { t, _ -> closeable = t }
      .thenCompose { it.isOpenAsync().closeOnCompletion(it) }
      .toCompletableFuture()
      .get()

    assertTrue(
      actual = isOpen,
      message = "AsyncCloseable is open during use",
    )

    assertTrue(
      actual = closeable!!.isClosed,
      message = "AsyncCloseable is closed after use",
    )
  }

  @Test
  fun `test 'closeOnCompletion' on exceptionally completed future`() {
    var closeable: TestCloseable? = null

    val exception = assertFailsWith<ExecutionException> {
      newCloseable()
        .whenComplete { t, _ -> closeable = t }
        .thenCompose { it.isOpenAsyncFailed().closeOnCompletion(it) }
        .toCompletableFuture()
        .get()
    }.cause as TestCloseableException

    assertTrue(
      actual = exception.isOpen,
      message = "AsyncCloseable is open during use",
    )

    assertTrue(
      actual = closeable!!.isClosed,
      message = "AsyncCloseable is closed after use",
    )
  }

  @Test
  fun `test 'thenComposeUsing' on successfully completed future`() {
    var closeable: TestCloseable? = null

    val isOpen = newCloseable()
      .whenComplete { t, _ -> closeable = t }
      .thenComposeUsing { it.isOpenAsync() }
      .toCompletableFuture()
      .get()

    assertTrue(
      actual = isOpen,
      message = "AsyncCloseable is open during use",
    )

    assertTrue(
      actual = closeable!!.isClosed,
      message = "AsyncCloseable is closed after use",
    )
  }

  @Test
  fun `test 'thenComposeUsing' on exceptionally completed future`() {
    var closeable: TestCloseable? = null

    val exception = assertFailsWith<ExecutionException> {
      newCloseable()
        .whenComplete { t, _ -> closeable = t }
        .thenComposeUsing { it.isOpenAsyncFailed() }
        .toCompletableFuture()
        .get()
    }.cause as TestCloseableException

    assertTrue(
      actual = exception.isOpen,
      message = "AsyncCloseable is open during use",
    )

    assertTrue(
      actual = closeable!!.isClosed,
      message = "AsyncCloseable is closed after use",
    )
  }

  @Test
  fun `test 'thenApplyUsing' on successfully completed future`() {
    var closeable: TestCloseable? = null

    val isOpen = newCloseable()
      .whenComplete { t, _ -> closeable = t }
      .thenApplyUsing { it.isOpen }
      .toCompletableFuture()
      .get()

    assertTrue(
      actual = isOpen,
      message = "AsyncCloseable is open during use",
    )

    assertTrue(
      actual = closeable!!.isClosed,
      message = "AsyncCloseable is closed after use",
    )
  }

  @Test
  fun `test 'thenApplyUsing' on exceptionally completed future`() {
    var closeable: TestCloseable? = null

    val exception = assertFailsWith<ExecutionException> {
      newCloseable()
        .whenComplete { t, _ -> closeable = t }
        .thenApplyUsing { throw TestCloseableException(it.isOpen) }
        .toCompletableFuture()
        .get()
    }.cause as TestCloseableException

    assertTrue(
      actual = exception.isOpen,
      message = "AsyncCloseable is open during use",
    )

    assertTrue(
      actual = closeable!!.isClosed,
      message = "AsyncCloseable is closed after use",
    )
  }

  @Test
  fun `test 'suspendingUse' on successfully completed operation`() = runTest {
    val closeable = TestCloseable()
    val isOpen = closeable.suspendingUse { closeable.isOpen }

    assertTrue(
      actual = isOpen,
      message = "AsyncCloseable is open during use",
    )

    assertTrue(
      actual = closeable.isClosed,
      message = "AsyncCloseable is closed after use",
    )
  }

  @Test
  fun `test 'suspendingUse' on successfully failed operation`() = runTest {
    val closeable = TestCloseable()
    val exception = assertFailsWith<TestCloseableException> {
      closeable.suspendingUse { throw TestCloseableException(closeable.isOpen) }
    }

    assertTrue(
      actual = exception.isOpen,
      message = "AsyncCloseable is open during use",
    )

    assertTrue(
      actual = closeable.isClosed,
      message = "AsyncCloseable is closed after use",
    )
  }

  @Test
  fun `test 'closeFinallyAsync', with a cause,  when closeAsync throws an exception`() {
    val closeFailure = Exception("failed to close")
    val asyncCloseable = AsyncCloseable { CompletableFuture.failedFuture(closeFailure) }
    val cause = Exception("operation failed")

    asyncCloseable.closeFinallyAsync(cause)
      .toCompletableFuture().join()

    assertTrue(
      actual = cause.suppressed.contains(closeFailure),
      message = "closeFinallyAsync handles close exception and adds it as suppressed",
    )

  }

  @Test
  fun `test 'closeFinallyAsync', without a cause,  when closeAsync throws an exception`() {
    val closeFailure = Exception("failed to close")
    val asyncCloseable = AsyncCloseable { CompletableFuture.failedFuture(closeFailure) }

    try {
      asyncCloseable.closeFinallyAsync()
        .toCompletableFuture().join()
    } catch (e: Exception) {
      fail("closeFinallyAsync should not throw exception on close ", e)
    }
  }

  @Test
  fun `test 'closeFinallyAsync', without a cause,  when closeAsync succeeds`() {
    val asyncCloseable = TestCloseable()

    asyncCloseable.closeFinallyAsync()
      .toCompletableFuture().join()

    assertTrue(
      actual = asyncCloseable.isClosed,
      message = "AsyncCloseable is closed after closeFinallyAsync",
    )
  }
}
