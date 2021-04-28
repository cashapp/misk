package misk.logging

import misk.sampling.Sampler
import mu.KLogger

/**
 * Returns a logger that samples logs. This logger MUST be instantiated statically,
 * in a companion object or as a Singleton.
 *
 * To get a rate limited logger:
 *
 *   val logger = getLogger<MyClass>().sampled(RateLimitingSampler(RATE_PER_SECOND))
 *
 * To get a probabilistic sampler
 *
 *   val logger = getLogger<MyClass>().sampled(PercentSampler(PERCENTAGE_TO_ALLOW))
 */
fun KLogger.sampled(sampler: Sampler): KLogger {
  return SampledLogger(this, sampler)
}
