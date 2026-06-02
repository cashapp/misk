package misk.ratelimiting.bucket4j.dynamodb.v2.proxymanager

import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.proxy.BucketNotFoundException
import io.github.bucket4j.distributed.proxy.RecoveryStrategy
import io.github.bucket4j.distributed.proxy.optimization.DelayParameters
import io.github.bucket4j.distributed.proxy.optimization.NopeOptimizationListener
import io.github.bucket4j.distributed.proxy.optimization.PredictionParameters
import io.github.bucket4j.distributed.proxy.optimization.batch.BatchingOptimization
import io.github.bucket4j.distributed.proxy.optimization.delay.DelayOptimization
import io.github.bucket4j.distributed.proxy.optimization.manual.ManuallySyncingOptimization
import io.github.bucket4j.distributed.proxy.optimization.predictive.PredictiveOptimization
import io.github.bucket4j.distributed.proxy.optimization.skiponzero.SkipSyncOnZeroOptimization
import jakarta.inject.Inject
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wisp.ratelimiting.bucket4j.ClockTimeMeter

abstract class BaseBucketTest<K> {
  @Inject private lateinit var fakeClock: FakeClock

  @Test
  fun `should recreate lost buckets with recreation strategy`() {
    val key = createRandomKey()
    val proxyManager = createProxyManager()
    val bucket =
      proxyManager.builder().withRecoveryStrategy(RecoveryStrategy.RECONSTRUCT).build(key) { DEFAULT_BUCKET_CONFIG }

    assertThat(bucket.tryConsume(1)).isTrue()

    proxyManager.removeProxy(key)

    assertThat(bucket.tryConsume(1)).isTrue()
  }

  @Test
  fun `should throw with throwing strategy`() {
    val key = createRandomKey()
    val proxyManager = createProxyManager()
    val bucket =
      proxyManager.builder().withRecoveryStrategy(RecoveryStrategy.THROW_BUCKET_NOT_FOUND_EXCEPTION).build(key) {
        DEFAULT_BUCKET_CONFIG
      }

    assertThat(bucket.tryConsume(1)).isTrue()

    proxyManager.removeProxy(key)

    assertThrows<BucketNotFoundException> { bucket.tryConsume(1) }
  }

  @Test
  fun `should return configuration reflecting bucket state`() {
    val key = createRandomKey()
    val proxyManager = createProxyManager()

    // Have not constructed bucket for key, should be empty
    assertThat(proxyManager.getProxyConfiguration(key).isEmpty).isTrue()

    val bucket =
      proxyManager
        .builder()
        .withRecoveryStrategy(RecoveryStrategy.RECONSTRUCT)
        .build(key) { DEFAULT_BUCKET_CONFIG }
        .availableTokens
    // Constructed bucket for key, should not be empty
    assertThat(proxyManager.getProxyConfiguration(key).isEmpty).isFalse()

    // Remove bucket
    proxyManager.removeProxy(key)
    // Should be empty again
    assertThat(proxyManager.getProxyConfiguration(key).isEmpty).isTrue()
  }

  @Test
  fun `optimizations should all have consistent token availability`() {
    val proxyManager = createProxyManager()

    val bucketConfig =
      BucketConfiguration.builder().addLimit { it.capacity(10).refillIntervally(10, Duration.ofSeconds(1)) }.build()
    val delayParameters = DelayParameters(1, Duration.ofMillis(1))
    val timeMeter = ClockTimeMeter(fakeClock)
    val optimizations =
      listOf(
        BatchingOptimization(NopeOptimizationListener.INSTANCE),
        DelayOptimization(delayParameters, NopeOptimizationListener.INSTANCE, timeMeter),
        ManuallySyncingOptimization(NopeOptimizationListener.INSTANCE, timeMeter),
        PredictiveOptimization(
          PredictionParameters.createDefault(delayParameters),
          delayParameters,
          NopeOptimizationListener.INSTANCE,
          timeMeter,
        ),
        SkipSyncOnZeroOptimization(NopeOptimizationListener.INSTANCE, timeMeter),
      )
    optimizations.forEach { optimization ->
      val key = createRandomKey()
      val bucket = proxyManager.builder().withOptimization(optimization).build(key) { bucketConfig }
      assertThat(bucket.availableTokens).isEqualTo(10L)

      repeat(5) { assertThat(bucket.tryConsume(1)).isTrue() }
      proxyManager.removeProxy(key)

      bucket.forceAddTokens(90)
      // Removal resets bucket back to 10 tokens from config + 90 from force add equals 100
      assertThat(bucket.availableTokens).isEqualTo(100L)

      // Remove the bucket again and check verbose response
      proxyManager.removeProxy(key)
      bucket.asVerbose().forceAddTokens(90)
      assertThat(bucket.asVerbose().availableTokens.value).isEqualTo(100L)
    }
  }

  @Test
  fun `parallel initialization of the same bucket should be well behaved`() {
    val key = createRandomKey()
    val parallelism =
      if (Runtime.getRuntime().availableProcessors() > 1) {
        Runtime.getRuntime().availableProcessors()
      } else {
        2
      }
    val bucketConfig =
      BucketConfiguration.builder()
        .addLimit { it.capacity((parallelism + 10).toLong()).refillIntervally(10, Duration.ofSeconds(1)) }
        .build()

    val startLatch = CountDownLatch(parallelism)
    val stopLatch = CountDownLatch(parallelism)
    val executor = Executors.newFixedThreadPool(parallelism)
    repeat(parallelism) {
      executor.submit {
        startLatch.countDown()
        startLatch.await()
        createProxyManager().builder().build(key) { bucketConfig }.tryConsume(1)
        stopLatch.countDown()
      }
    }
    executor.shutdown()
    executor.awaitTermination(10, TimeUnit.SECONDS)
    stopLatch.await()

    val bucket = createProxyManager().builder().build(key) { bucketConfig }
    // We should have taken parallelism tokens, leaving the extra 10
    assertThat(bucket.availableTokens).isEqualTo(10)
  }

  internal abstract fun createProxyManager(): BaseDynamoDBProxyManager<K>

  internal abstract fun createRandomKey(): K

  companion object {
    private val DEFAULT_BUCKET_CONFIG =
      BucketConfiguration.builder()
        .addLimit { it.capacity(100).refillIntervally(100, Duration.ofMinutes(10)) }
        .addLimit { it.capacity(10).refillIntervally(10, Duration.ofSeconds(1)) }
        .build()
  }
}
