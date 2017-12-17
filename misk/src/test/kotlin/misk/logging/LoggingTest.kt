package misk.logging

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LoggingTest {
  private val logger = getLogger<LoggingTest>()

  @Test
  fun loggerNameIsBasedOnTheOuterClass() {
    assertThat(logger.name).isEqualTo("misk.logging.LoggingTest")
  }
}
