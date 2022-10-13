package wisp.sampling

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SamplerTest {
    @Test
    fun `always sampler always allows samples`() {
        val sampler = AlwaysSampler()

        assertThat(sampler.sample()).isTrue
    }

    @Test
    fun `percentage sampler allows sample when random value is below percentage`() {
        val sampler = PercentageSampler(50) { 49 }

        assertThat(sampler.sample()).isTrue
    }

    @Test
    fun `percentage sampler does not allow sample when random value is equal to percentage`() {
        val sampler = PercentageSampler(50) { 50 }

        assertThat(sampler.sample()).isFalse
    }

    @Test
    fun `percentage sampler does not allow sample when random value is above percentage`() {
        val sampler = PercentageSampler(50) { 51 }

        assertThat(sampler.sample()).isFalse
    }

    @Test
    fun `rate limiting sampler allows sample when request is below threshold`() {
        val sampler = RateLimitingSampler(RateLimiter(1L))

        assertThat(sampler.sample()).isTrue
    }

    @Test
    fun `rate limiting sampler does not allow sample when request is above threshold`() {
        val sampler = RateLimitingSampler(RateLimiter(0L))

        assertThat(sampler.sample()).isFalse
    }
}

