package misk.web.concurrencylimits

import com.google.inject.Provider
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
import jakarta.inject.Singleton
import java.time.Clock
import misk.Action
import misk.inject.KAbstractModule
import misk.web.ConcurrencyLimiterConfig
import misk.web.interceptors.AlwaysEnabledMiskConcurrencyLimiterFeature
import misk.web.interceptors.MiskConcurrencyLimiterFeature

class ConcurrencyLimitsModule(private val config: ConcurrencyLimiterConfig) : KAbstractModule() {

  override fun configure() {
    // Establishes an optional binding for a Feature to dynamically enable/disable the concurrency
    // limiter in the interceptor. It also sets the default to always enabled. It will use the
    // default unless there is a binding that ovewrites it.
    bindOptionalDefault<MiskConcurrencyLimiterFeature>().toInstance(AlwaysEnabledMiskConcurrencyLimiterFeature)
  }

  @ProvidesIntoSet
  @Singleton
  fun concurrencyLimiterFactory(limit: Provider<Limit>, clock: Clock): ConcurrencyLimiterFactory =
    /**
     * This will create the SimpleLimiter with the same Limit algorithm for each every action. This can be configured
     * per action if needed.
     */
    object : ConcurrencyLimiterFactory {
      override fun create(action: Action): Limiter<String>? =
        SimpleLimiter.Builder().clock { clock.millis() }.named(action.name).limit(limit.get()).build()
    }

  @Provides
  private fun limit(): Limit =
    when (config.strategy) {
      ConcurrencyLimiterStrategy.VEGAS ->
        VegasLimit.newBuilder()
          .apply {
            config.initial_limit?.let { initialLimit(it) }
            config.max_concurrency?.let { maxConcurrency(it) }
          }
          .build()

      ConcurrencyLimiterStrategy.GRADIENT ->
        GradientLimit.newBuilder()
          .apply {
            config.initial_limit?.let { initialLimit(it) }
            config.max_concurrency?.let { maxConcurrency(it) }
            config.min_limit?.let { minLimit(it) }
          }
          .build()

      ConcurrencyLimiterStrategy.GRADIENT2 ->
        Gradient2Limit.newBuilder()
          .apply {
            config.initial_limit?.let { initialLimit(it) }
            config.max_concurrency?.let { maxConcurrency(it) }
            config.min_limit?.let { minLimit(it) }
          }
          .build()

      ConcurrencyLimiterStrategy.AIMD ->
        AIMDLimit.newBuilder()
          .apply {
            config.initial_limit?.let { initialLimit(it) }
            config.max_concurrency?.let { maxLimit(it) }
            config.min_limit?.let { minLimit(it) }
          }
          .build()

      ConcurrencyLimiterStrategy.SETTABLE ->
        config.max_concurrency?.let { SettableLimit.startingAt(it) }
          ?: throw IllegalStateException("SettableLimit algorithm requires 'maxConcurrency'")

      ConcurrencyLimiterStrategy.FIXED ->
        config.max_concurrency?.let { FixedLimit.of(it) }
          ?: throw IllegalStateException("FixedLimit algorithm requires 'maxConcurrency'")
    }
}
