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
  fun `getQueueConfig returns all_queues when no per_queue_override exists`() {
    val config = SqsConfig(all_queues = SqsQueueConfig(concurrency = 10, parallelism = 5))

    val queueConfig = config.getQueueConfig(misk.jobqueue.QueueName("test-queue"))

    assertEquals(10, queueConfig.concurrency)
    assertEquals(5, queueConfig.parallelism)
  }

  @Test
  fun `getQueueConfig returns per_queue_override when it exists`() {
    val config =
      SqsConfig(
        all_queues = SqsQueueConfig(concurrency = 1, parallelism = 1),
        per_queue_overrides = mapOf("test-queue" to SqsQueueConfig(concurrency = 20, parallelism = 10)),
      )

    val queueConfig = config.getQueueConfig(misk.jobqueue.QueueName("test-queue"))

    assertEquals(20, queueConfig.concurrency)
    assertEquals(10, queueConfig.parallelism)
  }

  @Test
  fun `getQueueConfig inherits nullable fields from all_queues`() {
    val config =
      SqsConfig(
        all_queues = SqsQueueConfig(region = "us-west-2", wait_timeout = 20),
        per_queue_overrides = mapOf("test-queue" to SqsQueueConfig(concurrency = 10)),
      )

    val queueConfig = config.getQueueConfig(misk.jobqueue.QueueName("test-queue"))

    assertEquals(10, queueConfig.concurrency)
    assertEquals("us-west-2", queueConfig.region) // inherited from all_queues
    assertEquals(20, queueConfig.wait_timeout) // inherited from all_queues
  }

  @Test
  fun `drain_timeout_ms has null default`() {
    val config = SqsConfig()
    assertEquals(null, config.getQueueConfig(misk.jobqueue.QueueName("test-queue")).drain_timeout_ms)
  }

  @Test
  fun `getQueueConfig inherits drain_timeout_ms from all_queues and allows overriding it`() {
    val config =
      SqsConfig(
        all_queues = SqsQueueConfig(drain_timeout_ms = 15_000),
        per_queue_overrides =
          mapOf(
            "inheriting-queue" to SqsQueueConfig(concurrency = 10),
            "overriding-queue" to SqsQueueConfig(drain_timeout_ms = 5_000),
          ),
      )

    assertEquals(15_000, config.getQueueConfig(misk.jobqueue.QueueName("inheriting-queue")).drain_timeout_ms)
    assertEquals(5_000, config.getQueueConfig(misk.jobqueue.QueueName("overriding-queue")).drain_timeout_ms)
    assertEquals(15_000, config.getQueueConfig(misk.jobqueue.QueueName("unlisted-queue")).drain_timeout_ms)
  }
}
