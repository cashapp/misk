package misk.aws2.sqs.jobqueue.config

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SqsConfigTest {
  @Test
  fun `buffered_batch_flush_frequency_ms has default value of 50`() {
    val config = SqsConfig()
    assertEquals(50, config.buffered_batch_flush_frequency_ms)
  }

  @Test
  fun `buffered_batch_flush_frequency_ms can be customized`() {
    val config = SqsConfig(buffered_batch_flush_frequency_ms = 100)
    assertEquals(100, config.buffered_batch_flush_frequency_ms)
  }

  @Test
  fun `config_feature_flag has null default`() {
    val config = SqsConfig()
    assertEquals(null, config.config_feature_flag)
  }

  @Test
  fun `config_feature_flag can be customized`() {
    val config = SqsConfig(config_feature_flag = "sqs-config-override")
    assertEquals("sqs-config-override", config.config_feature_flag)
  }

  @Test
  fun `hasFeatureFlag returns false when no flag configured`() {
    val config = SqsConfig()
    assertFalse(config.hasFeatureFlag())
  }

  @Test
  fun `hasFeatureFlag returns true when flag configured`() {
    val config = SqsConfig(config_feature_flag = "sqs-config-override")
    assertTrue(config.hasFeatureFlag())
  }

  @Test
  fun `applyOverride overrides all_queues values`() {
    val base = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 1, parallelism = 1),
    )
    val override = SqsConfigOverride(
      all_queues = SqsQueueConfigOverride(concurrency = 10, parallelism = 5),
    )

    val result = base.applyOverride(override)

    assertEquals(10, result.all_queues.concurrency)
    assertEquals(5, result.all_queues.parallelism)
  }

  @Test
  fun `applyOverride preserves base values when override fields are null`() {
    val base = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 10, parallelism = 5),
    )
    val override = SqsConfigOverride(
      all_queues = SqsQueueConfigOverride(), // all nulls
    )

    val result = base.applyOverride(override)

    // Base values preserved because override fields are null
    assertEquals(10, result.all_queues.concurrency)
    assertEquals(5, result.all_queues.parallelism)
  }

  @Test
  fun `applyOverride can set values to defaults explicitly`() {
    val base = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 50, parallelism = 10),
    )
    val override = SqsConfigOverride(
      all_queues = SqsQueueConfigOverride(concurrency = 1, parallelism = 1), // explicitly set to defaults
    )

    val result = base.applyOverride(override)

    // Override values applied even though they match defaults
    assertEquals(1, result.all_queues.concurrency)
    assertEquals(1, result.all_queues.parallelism)
  }

  @Test
  fun `applyOverride adds new per_queue_overrides`() {
    val base = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 1, parallelism = 1),
      per_queue_overrides = mapOf(
        "queue-a" to SqsQueueConfig(concurrency = 5),
      ),
    )
    val override = SqsConfigOverride(
      per_queue_overrides = mapOf(
        "queue-b" to SqsQueueConfigOverride(concurrency = 10),
      ),
    )

    val result = base.applyOverride(override)

    assertEquals(2, result.per_queue_overrides.size)
    assertEquals(5, result.per_queue_overrides["queue-a"]?.concurrency)
    assertEquals(10, result.per_queue_overrides["queue-b"]?.concurrency)
  }

  @Test
  fun `applyOverride merges existing per_queue_overrides`() {
    val base = SqsConfig(
      per_queue_overrides = mapOf(
        "queue-a" to SqsQueueConfig(concurrency = 5, parallelism = 2),
      ),
    )
    val override = SqsConfigOverride(
      per_queue_overrides = mapOf(
        "queue-a" to SqsQueueConfigOverride(concurrency = 15), // only override concurrency
      ),
    )

    val result = base.applyOverride(override)

    assertEquals(15, result.per_queue_overrides["queue-a"]?.concurrency)
    assertEquals(2, result.per_queue_overrides["queue-a"]?.parallelism) // preserved from base
  }

  @Test
  fun `applyOverride overrides buffered_batch_flush_frequency_ms when set`() {
    val base = SqsConfig(buffered_batch_flush_frequency_ms = 50)
    val override = SqsConfigOverride(buffered_batch_flush_frequency_ms = 100)

    val result = base.applyOverride(override)

    assertEquals(100, result.buffered_batch_flush_frequency_ms)
  }

  @Test
  fun `applyOverride preserves buffered_batch_flush_frequency_ms when override is null`() {
    val base = SqsConfig(buffered_batch_flush_frequency_ms = 100)
    val override = SqsConfigOverride(buffered_batch_flush_frequency_ms = null)

    val result = base.applyOverride(override)

    assertEquals(100, result.buffered_batch_flush_frequency_ms)
  }

  @Test
  fun `applyOverride can set buffered_batch_flush_frequency_ms to default explicitly`() {
    val base = SqsConfig(buffered_batch_flush_frequency_ms = 100)
    val override = SqsConfigOverride(buffered_batch_flush_frequency_ms = 50) // explicitly set to default

    val result = base.applyOverride(override)

    assertEquals(50, result.buffered_batch_flush_frequency_ms)
  }

  @Test
  fun `applyOverride creates new per_queue_override from all_queues when queue not in base`() {
    val base = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 5, parallelism = 2),
    )
    val override = SqsConfigOverride(
      per_queue_overrides = mapOf(
        "new-queue" to SqsQueueConfigOverride(concurrency = 10),
      ),
    )

    val result = base.applyOverride(override)

    // New queue should inherit from all_queues and apply override
    assertEquals(10, result.per_queue_overrides["new-queue"]?.concurrency)
    assertEquals(2, result.per_queue_overrides["new-queue"]?.parallelism) // from all_queues
  }
}

class SqsQueueConfigTest {
  @Test
  fun `applyOverride overrides non-null values`() {
    val base = SqsQueueConfig(concurrency = 1, parallelism = 1)
    val override = SqsQueueConfigOverride(concurrency = 10, parallelism = 5)

    val result = base.applyOverride(override)

    assertEquals(10, result.concurrency)
    assertEquals(5, result.parallelism)
  }

  @Test
  fun `applyOverride preserves base values when override fields are null`() {
    val base = SqsQueueConfig(concurrency = 10, parallelism = 5, channel_capacity = 20)
    val override = SqsQueueConfigOverride() // all nulls

    val result = base.applyOverride(override)

    assertEquals(10, result.concurrency)
    assertEquals(5, result.parallelism)
    assertEquals(20, result.channel_capacity)
  }

  @Test
  fun `applyOverride can set values to defaults explicitly`() {
    val base = SqsQueueConfig(concurrency = 50, parallelism = 10, channel_capacity = 20)
    val override = SqsQueueConfigOverride(concurrency = 1, parallelism = 1, channel_capacity = 0)

    val result = base.applyOverride(override)

    assertEquals(1, result.concurrency)
    assertEquals(1, result.parallelism)
    assertEquals(0, result.channel_capacity)
  }

  @Test
  fun `applyOverride handles nullable fields`() {
    val base = SqsQueueConfig(wait_timeout = 10, visibility_timeout = 30)
    val override = SqsQueueConfigOverride(wait_timeout = 20) // visibility_timeout is null

    val result = base.applyOverride(override)

    assertEquals(20, result.wait_timeout)
    assertEquals(30, result.visibility_timeout) // preserved from base
  }

  @Test
  fun `applyOverride overrides nullable fields when set`() {
    val base = SqsQueueConfig(region = "us-west-2", account_id = "123456")
    val override = SqsQueueConfigOverride(region = "us-east-1")

    val result = base.applyOverride(override)

    assertEquals("us-east-1", result.region)
    assertEquals("123456", result.account_id) // preserved from base
  }
}
