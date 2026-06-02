package wisp.sampling

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(expression = "Sampler", imports = ["misk.sampling.Sampler"]),
)
/**
 * A [Sampler] is used to "sample" whether an action is allowed to occur or not.
 *
 * A common usage of the sampler would look like:
 * ```kt
 * if (sampler.sample()) {
 *     performAction()
 * }
 * ```
 *
 * The frequency at which `sample` returns `true` or `false` is based on the implementation's policy. For example,
 * [Sampler.always] creates a [Sampler] that only returns `true`, while [Sampler.percentage] will only return `true` for
 * a given percentage of samples. For a more complex example, [Sampler.rateLimiting] will limit the number of `true`
 * samples to a given rate per second.
 */
interface Sampler {
  /**
   * Tests if a sample is allowed or not
   *
   * @return `true` if sample is allowed, otherwise `false`
   */
  fun sample(): Boolean

  companion object {
    /**
     * Creates a [Sampler] that limits positive results to a percentage chance.
     *
     * @param samplePercentage percentage chance of positive results
     * @return percentage [Sampler] instance
     */
    fun percentage(samplePercentage: Int): Sampler =
      PercentageSampler(samplePercentage) { ThreadLocalRandom.current().nextInt(0, 100) }

    /**
     * Creates a [Sampler] that limits positive results to a limited rate per second.
     *
     * @param ratePerSecond the number of positive results per second
     * @return rate limiting [Sampler] instance
     */
    fun rateLimiting(ratePerSecond: Long): Sampler = RateLimitingSampler(RateLimiter(ratePerSecond))

    /**
     * Creates a [Sampler] that always returns positive results.
     *
     * @return always [Sampler] instance
     */
    fun always(): Sampler = AlwaysSampler()
  }
}

@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(expression = "PercentSampler(samplePercentage, random)", imports = ["misk.sampling"]),
)
class PercentageSampler(private val samplePercentage: Int, private val random: () -> Int) : Sampler {
  override fun sample(): Boolean = random() < samplePercentage
}

@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(expression = "RateLimitingSampler(rateLimiter)", imports = ["misk.sampling"]),
)
class RateLimitingSampler(private val rateLimiter: RateLimiter) : Sampler {
  override fun sample(): Boolean {
    return rateLimiter.tryAcquire(1L, 0, TimeUnit.SECONDS)
  }
}

@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(expression = "AlwaysSampler()", imports = ["misk.sampling"]),
)
class AlwaysSampler : Sampler {
  override fun sample(): Boolean = true
}
