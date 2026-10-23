package misk.redis.lettuce

import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.RedisConnectionException
import io.lettuce.core.RedisLoadingException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

internal class RetryingFunctionCodeLoaderTest {
  @Test
  fun `retries transient failures until the code loads`() {
    val loader = FakeFunctionCodeLoader(RedisConnectionException("unreachable"), RedisLoadingException("LOADING"))

    loader.loadWithRetries(shouldRetry = { true }).get(10, TimeUnit.SECONDS)

    assertEquals(3, loader.attempts.get())
  }

  @Test
  fun `doesn't retry when Redis rejects the code`() {
    val loader = FakeFunctionCodeLoader(RedisCommandExecutionException("ERR Error compiling function"))

    val e =
      assertFailsWith<ExecutionException> { loader.loadWithRetries(shouldRetry = { true }).get(10, TimeUnit.SECONDS) }

    assertIs<RedisCommandExecutionException>(e.cause)
    assertEquals(1, loader.attempts.get())
  }

  private class FakeFunctionCodeLoader(private vararg val failures: Exception) :
    RetryingFunctionCodeLoader("redis/testlib.lua") {
    val attempts = AtomicInteger()

    override val destination = "fake"

    override fun loadOnce(code: String): CompletionStage<String> {
      val failure = failures.getOrNull(attempts.getAndIncrement())
      return if (failure != null) CompletableFuture.failedFuture(failure)
      else CompletableFuture.completedFuture("testlib")
    }
  }
}
