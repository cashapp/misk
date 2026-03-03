package misk.sampling

import com.google.common.base.Ticker
import misk.concurrent.Sleeper
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
  private val rateLimiter: RateLimiter
) : Sampler {
  constructor(ratePerSecond: Long) : this(RateLimiter.Factory(Ticker.systemTicker(), Sleeper.DEFAULT).create(1))

  override fun sample(): Boolean {
    return rateLimiter.tryAcquire(1L, 0, TimeUnit.SECONDS)
  }
}

/** Sampler that always invokes an action */
@Singleton
class AlwaysSampler @Inject constructor() : Sampler {
  override fun sample(): Boolean = true
}
