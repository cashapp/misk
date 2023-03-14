package com.squareup.exemplar

import com.google.inject.Module
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import javax.inject.Inject

@MiskTest
class FakeClockTest {
  @MiskTestModule val module: Module = ExemplarTestModule()

  @Inject private lateinit var fakeClock: FakeClock

  @Test
  fun checkCompilation() {
    // As long as this compiles, it's all good
    assertThat(fakeClock.instant()).isEqualTo(Instant.parse("2018-01-01T00:00:00Z"))
  }
}
