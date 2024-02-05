package misk.sampling

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SamplerTest {
  @Test
  fun `rate limiter sampler constructor sets rate`() {
    val sampler = RateLimitingSampler(2L)
    assertThat(sampler.sample()).isTrue()
    assertThat(sampler.sample()).isTrue()
    assertThat(sampler.sample()).isFalse()
  }
}
