package misk.metrics.v2

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

internal class MetricsKtTest {
  @Test fun `linear buckets`() {
    val buckets = linearBuckets(2.0, 1.0, 5)
    assertEquals(5, buckets.size)
    assertEquals(buckets, listOf(2.0, 3.0, 4.0, 5.0, 6.0))
  }

  @Test fun `exponential buckets`() {
    val buckets = exponentialBuckets(2.0, 2.0, 5)
    assertEquals(5, buckets.size)
    assertEquals(buckets, listOf(2.0, 4.0, 8.0, 16.0, 32.0))
  }
}
