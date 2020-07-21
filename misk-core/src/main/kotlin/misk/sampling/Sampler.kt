package misk.sampling

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface Sampler {
  /** If an action should be taken based on the implementation's policy, returns true */
  fun sample(): Boolean

  /** If [sample] returns true, runs the given lambda */
  fun sampledCall(f: () -> Unit) {
    if (sample()) {
      f()
    }
  }
}

/** A [Sampler] randomly invokes an action based on a sample percentage */
class PercentSampler(
  val samplePercentage: () -> Int,
  val random: () -> Int = { ThreadLocalRandom.current().nextInt(0, 100) }
) : Sampler {
  constructor(samplePercentage: Int, random: () -> Int) : this({ samplePercentage }, random)
  constructor(samplePercentage: Int) : this({ samplePercentage })

  override fun sample(): Boolean = random() < samplePercentage()
}

class RateLimitingSampler(
  val rateLimiter: RateLimiter
) : Sampler {
  override fun sample(): Boolean {
    return rateLimiter.tryAcquire(1L, 0, TimeUnit.SECONDS)
  }

  class Factory @Inject constructor(
    private val rateLimiterFactory: RateLimiter.Factory
  ) {
    fun createSampler(ratePerSecond: Long): RateLimitingSampler {
      return RateLimitingSampler(rateLimiterFactory.create(ratePerSecond))
    }
  }
}


/** Sampler that always invokes an action */
@Singleton
class AlwaysSampler @Inject constructor() : Sampler {
  override fun sample(): Boolean = true
}
