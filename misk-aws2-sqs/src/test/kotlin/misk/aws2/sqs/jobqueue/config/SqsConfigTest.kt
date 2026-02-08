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
  fun `feature flag fields have null defaults`() {
    val config = SqsConfig()
    assertEquals(null, config.concurrency_feature_flag)
    assertEquals(null, config.parallelism_feature_flag)
  }

  @Test
  fun `feature flag fields can be customized`() {
    val config = SqsConfig(
      concurrency_feature_flag = "pod-jobqueue-consumers",
      parallelism_feature_flag = "pod-jobqueue-parallelism",
    )
    assertEquals("pod-jobqueue-consumers", config.concurrency_feature_flag)
    assertEquals("pod-jobqueue-parallelism", config.parallelism_feature_flag)
  }

  @Test
  fun `hasFeatureFlags returns false when no flags configured`() {
    val config = SqsConfig()
    assertFalse(config.hasFeatureFlags())
  }

  @Test
  fun `hasFeatureFlags returns true when concurrency flag configured`() {
    val config = SqsConfig(concurrency_feature_flag = "pod-jobqueue-consumers")
    assertTrue(config.hasFeatureFlags())
  }

  @Test
  fun `hasFeatureFlags returns true when parallelism flag configured`() {
    val config = SqsConfig(parallelism_feature_flag = "pod-jobqueue-parallelism")
    assertTrue(config.hasFeatureFlags())
  }

  @Test
  fun `hasFeatureFlags returns true when both flags configured`() {
    val config = SqsConfig(
      concurrency_feature_flag = "pod-jobqueue-consumers",
      parallelism_feature_flag = "pod-jobqueue-parallelism",
    )
    assertTrue(config.hasFeatureFlags())
  }
}
