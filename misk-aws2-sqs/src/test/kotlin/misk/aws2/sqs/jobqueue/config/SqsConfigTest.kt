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
  fun `mergeWith overrides all_queues values`() {
    val base = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 1, parallelism = 1),
    )
    val override = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 10, parallelism = 5),
    )

    val merged = base.mergeWith(override)

    assertEquals(10, merged.all_queues.concurrency)
    assertEquals(5, merged.all_queues.parallelism)
  }

  @Test
  fun `mergeWith preserves base values when override uses defaults`() {
    val base = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 10, parallelism = 5),
    )
    val override = SqsConfig(
      all_queues = SqsQueueConfig(), // defaults: concurrency=1, parallelism=1
    )

    val merged = base.mergeWith(override)

    // Base values preserved because override uses default values
    assertEquals(10, merged.all_queues.concurrency)
    assertEquals(5, merged.all_queues.parallelism)
  }

  @Test
  fun `mergeWith adds new per_queue_overrides`() {
    val base = SqsConfig(
      all_queues = SqsQueueConfig(concurrency = 1, parallelism = 1),
      per_queue_overrides = mapOf(
        "queue-a" to SqsQueueConfig(concurrency = 5),
      ),
    )
    val override = SqsConfig(
      per_queue_overrides = mapOf(
        "queue-b" to SqsQueueConfig(concurrency = 10),
      ),
    )

    val merged = base.mergeWith(override)

    assertEquals(2, merged.per_queue_overrides.size)
    assertEquals(5, merged.per_queue_overrides["queue-a"]?.concurrency)
    assertEquals(10, merged.per_queue_overrides["queue-b"]?.concurrency)
  }

  @Test
  fun `mergeWith merges existing per_queue_overrides`() {
    val base = SqsConfig(
      per_queue_overrides = mapOf(
        "queue-a" to SqsQueueConfig(concurrency = 5, parallelism = 2),
      ),
    )
    val override = SqsConfig(
      per_queue_overrides = mapOf(
        "queue-a" to SqsQueueConfig(concurrency = 15), // only override concurrency
      ),
    )

    val merged = base.mergeWith(override)

    assertEquals(15, merged.per_queue_overrides["queue-a"]?.concurrency)
    assertEquals(2, merged.per_queue_overrides["queue-a"]?.parallelism) // preserved from base
  }

  @Test
  fun `mergeWith overrides buffered_batch_flush_frequency_ms when non-default`() {
    val base = SqsConfig(buffered_batch_flush_frequency_ms = 50)
    val override = SqsConfig(buffered_batch_flush_frequency_ms = 100)

    val merged = base.mergeWith(override)

    assertEquals(100, merged.buffered_batch_flush_frequency_ms)
  }

  @Test
  fun `mergeWith preserves buffered_batch_flush_frequency_ms when override is default`() {
    val base = SqsConfig(buffered_batch_flush_frequency_ms = 100)
    val override = SqsConfig(buffered_batch_flush_frequency_ms = 50) // default value

    val merged = base.mergeWith(override)

    assertEquals(100, merged.buffered_batch_flush_frequency_ms)
  }
}

class SqsQueueConfigTest {
  @Test
  fun `mergeWith overrides non-default values`() {
    val base = SqsQueueConfig(concurrency = 1, parallelism = 1)
    val override = SqsQueueConfig(concurrency = 10, parallelism = 5)

    val merged = base.mergeWith(override)

    assertEquals(10, merged.concurrency)
    assertEquals(5, merged.parallelism)
  }

  @Test
  fun `mergeWith preserves base values when override uses defaults`() {
    val base = SqsQueueConfig(concurrency = 10, parallelism = 5, channel_capacity = 20)
    val override = SqsQueueConfig() // all defaults

    val merged = base.mergeWith(override)

    assertEquals(10, merged.concurrency)
    assertEquals(5, merged.parallelism)
    assertEquals(20, merged.channel_capacity)
  }

  @Test
  fun `mergeWith handles nullable fields`() {
    val base = SqsQueueConfig(wait_timeout = 10, visibility_timeout = 30)
    val override = SqsQueueConfig(wait_timeout = 20) // visibility_timeout is null

    val merged = base.mergeWith(override)

    assertEquals(20, merged.wait_timeout)
    assertEquals(30, merged.visibility_timeout) // preserved from base
  }

  @Test
  fun `mergeWith overrides nullable fields when set`() {
    val base = SqsQueueConfig(region = "us-west-2", account_id = "123456")
    val override = SqsQueueConfig(region = "us-east-1")

    val merged = base.mergeWith(override)

    assertEquals("us-east-1", merged.region)
    assertEquals("123456", merged.account_id) // preserved from base
  }
}
