package misk.sampling

import com.google.common.base.Ticker
import misk.concurrent.Sleeper
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * A [Sampler] is used to provide "sampled" probabilistic execution of a function.
 *
 * A common usage of the sampler would look like:
 *
 * ```kt
 * if (sampler.sample()) {
 *     performAction()
 * }
 * ```
 *
 * The frequency at which `sample` returns `true` or `false` is based on the implementation's policy. For example,
 * [Sampler.always] creates a [Sampler] that only returns `true`, while [Sampler.percentage] will only return `true` for a
 * given percentage of samples. For a more complex example, [Sampler.rateLimiting] will limit the number of `true`
 * samples to a given rate per second.
 */
interface Sampler {
  /** If an action should be taken based on the implementation's policy, returns true */
  fun sample(): Boolean

  /** If [sample] returns true, runs the given lambda */
  fun sampledCall(f: () -> Unit) {
    if (sample()) {
      f()
    }
  }

  companion object {
    /**
     * Creates a [Sampler] that limits positive results to a percentage chance.
     *
     * @param samplePercentage percentage chance of positive results
     *
     * @return percentage [Sampler] instance
     */
    fun percentage(samplePercentage: Int): Sampler = PercentSampler(samplePercentage) {
      ThreadLocalRandom.current().nextInt(0, 100)
    }

    /**
     * Creates a [Sampler] that limits positive results to a limited rate per second.
     *
     * @param ratePerSecond the number of positive results per second
     *
     * @return rate limiting [Sampler] instance
     */
    fun rateLimiting(ratePerSecond: Long): Sampler = RateLimitingSampler(RateLimiter.Factory(
      Ticker.systemTicker(),
      Sleeper.DEFAULT,
    ).create(ratePerSecond))

    /**
     * Creates a [Sampler] that always returns positive results.
     *
     * @return always [Sampler] instance
     */
    fun always(): Sampler = AlwaysSampler()
  }
}

/** A [Sampler] randomly invokes an action based on a sample percentage */
class PercentSampler @JvmOverloads constructor(
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
  constructor(ratePerSecond: Long) : this(
    RateLimiter.Factory(
      Ticker.systemTicker(),
      Sleeper.DEFAULT
    ).create(ratePerSecond)
  )

  override fun sample(): Boolean {
    return rateLimiter.tryAcquire(1L, 0, TimeUnit.SECONDS)
  }
}

/** Sampler that always invokes an action */
@Singleton
class AlwaysSampler @Inject constructor() : Sampler {
  override fun sample(): Boolean = true
}
