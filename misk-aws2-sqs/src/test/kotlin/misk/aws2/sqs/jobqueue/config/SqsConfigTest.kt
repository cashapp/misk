package misk.aws2.sqs.jobqueue.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
}
