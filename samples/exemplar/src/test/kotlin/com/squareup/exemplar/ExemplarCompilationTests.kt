package com.squareup.exemplar

import com.google.inject.Module
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import misk.tokens.FakeTokenGenerator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import javax.inject.Inject

@MiskTest
class ExemplarCompilationTests {
  @MiskTestModule val module: Module = ExemplarTestModule()

  @Inject private lateinit var fakeClock: FakeClock
  @Inject private lateinit var fakeTokenGenerator: FakeTokenGenerator

  @Test
  fun basic() {
    assertThat(fakeClock.instant()).isEqualTo(Instant.parse("2018-01-01T00:00:00Z"))
    assertThat(fakeTokenGenerator.generate()).isEqualTo("0000000000000000000000001")
  }
}
