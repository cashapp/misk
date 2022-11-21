package com.squareup.exemplar

import misk.time.FakeClock
import misk.testing.MiskTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest
class FakeClockTest {
  @Test
  fun checkCompilation() {
    // As long as this compiles, it's all good
    val myclock = misk.time.FakeClock()
    assertThat(myclock).isNotNull
  }
}
