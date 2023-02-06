package misk.web.concurrencylimits

import com.google.inject.Provides
import com.google.inject.multibindings.ProvidesIntoSet
import com.netflix.concurrency.limits.Limit
import com.netflix.concurrency.limits.Limiter
import com.netflix.concurrency.limits.limit.AIMDLimit
import com.netflix.concurrency.limits.limit.FixedLimit
import com.netflix.concurrency.limits.limit.Gradient2Limit
import com.netflix.concurrency.limits.limit.GradientLimit
import com.netflix.concurrency.limits.limit.SettableLimit
import com.netflix.concurrency.limits.limit.VegasLimit
import com.netflix.concurrency.limits.limiter.SimpleLimiter
import misk.Action
import misk.inject.KAbstractModule
import misk.web.WebConfig
import java.time.Clock
import javax.inject.Provider
import javax.inject.Singleton

class ConcurrencyLimitsModule(
  private val webConfig: WebConfig
) : KAbstractModule() {
  @ProvidesIntoSet
  @Singleton
  fun concurrencyLimiterFactory(
    limit: Provider<Limit>,
    clock: Clock
  ): ConcurrencyLimiterFactory =
    /**
     * This will create the SimpleLimiter with the same Limit algorithm
     * for each every action. This can be configured per action if needed.
     */
    object : ConcurrencyLimiterFactory {
      override fun create(action: Action): Limiter<String>? = SimpleLimiter.Builder()
        .clock { clock.millis() }
        .named(action.name)
        .limit(limit.get())
        .build()
    }

  private fun limit() = when (webConfig.concurrency_limiter_strategy) {
    ConcurrencyLimiterStrategy.VEGAS ->
      VegasLimit.newBuilder().apply {
        webConfig.concurrency_limiter_max_concurrency?.let { maxConcurrency(it) }
      }.build()
    ConcurrencyLimiterStrategy.GRADIENT ->
      GradientLimit.newBuilder().apply {
        webConfig.concurrency_limiter_max_concurrency?.let { maxConcurrency(it) }
      }.build()
    ConcurrencyLimiterStrategy.GRADIENT2 ->
      Gradient2Limit.newBuilder().apply {
        webConfig.concurrency_limiter_max_concurrency?.let { maxConcurrency(it) }
      }.build()
    ConcurrencyLimiterStrategy.AIMD ->
      AIMDLimit.newBuilder().apply {
        initialLimit(webConfig.concurrency_limiter_initial_limit)
        webConfig.concurrency_limiter_max_concurrency?.let { maxLimit(it) }
      }.build()
    ConcurrencyLimiterStrategy.SETTABLE ->
      webConfig.concurrency_limiter_max_concurrency
        ?.let { SettableLimit.startingAt(it) }
        ?: throw IllegalStateException("SettableLimit algorithm requires 'maxConcurrency'")
    ConcurrencyLimiterStrategy.FIXED ->
      webConfig.concurrency_limiter_max_concurrency
        ?.let { FixedLimit.of(it) }
        ?: throw IllegalStateException("FixedLimitx algorithm requires 'maxConcurrency'")
  }
}
