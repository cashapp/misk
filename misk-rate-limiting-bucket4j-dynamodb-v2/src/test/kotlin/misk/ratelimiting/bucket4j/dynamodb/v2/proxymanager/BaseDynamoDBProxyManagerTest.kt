package misk.ratelimiting.bucket4j.dynamodb.v2.proxymanager

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.BucketConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

abstract class BaseDynamoDBProxyManagerTest<K> {
  @Test
  fun `should create and remove bucket`() {
    val key = createRandomKey()
    val proxyManager = createProxyManager()

    val configuration = BucketConfiguration.builder()
      .addLimit(
        Bandwidth.builder()
          .capacity(4)
          .refillIntervally(4, Duration.ofHours(1))
          .build()
      )
      .build()
    val bucket = proxyManager.builder().build(key) { configuration }
    // Populates the bucket in storage
    val tokens = bucket.availableTokens

    assertThat(proxyManager.getProxyConfiguration(key).isPresent).isTrue()
    proxyManager.removeProxy(key)
    assertThat(proxyManager.getProxyConfiguration(key).isPresent).isFalse()
  }

  internal abstract fun createProxyManager(): BaseDynamoDBProxyManager<K>

  internal abstract fun createRandomKey(): K
}
