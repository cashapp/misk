package misk.micrometer

import com.google.inject.Provider
import com.google.inject.multibindings.Multibinder
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.config.MeterFilter
import jakarta.inject.Inject
import misk.inject.KAbstractModule

/**
 * Installs Micrometer support for Misk applications.
 *
 * This module provides:
 * - A [CompositeMeterRegistry] that can contain multiple backend registries
 * - Multibindings for [MeterFilter] and [MeterBinder] to customize metrics
 * - A [Clock] for timing measurements
 *
 * To add backend-specific registries (e.g., Prometheus), install additional modules like [MicrometerPrometheusModule].
 *
 * Example:
 * ```
 * install(MicrometerModule())
 * install(MicrometerPrometheusModule())
 * ```
 */
class MicrometerModule : KAbstractModule() {
  override fun configure() {
    // Bind a system clock for metrics
    bind<Clock>().toInstance(Clock.SYSTEM)

    // Bind the composite registry
    bind<CompositeMeterRegistry>()
      .toProvider(
        object : Provider<CompositeMeterRegistry> {
          @Inject lateinit var clock: Clock

          override fun get() = CompositeMeterRegistry(clock)
        }
      )
      .asEagerSingleton()

    bind<MeterRegistry>().to<CompositeMeterRegistry>()

    // Create multibindings for filters and binders
    Multibinder.newSetBinder(binder(), MeterFilter::class.java)
    Multibinder.newSetBinder(binder(), MeterBinder::class.java)

    // Request injection for initialization
    requestInjection(MicrometerInitializer())
  }
}

/** Initializes the Micrometer registry with configured filters and binders. */
private class MicrometerInitializer {
  @Inject lateinit var registry: CompositeMeterRegistry
  @Inject lateinit var filters: Set<@JvmSuppressWildcards MeterFilter>
  @Inject lateinit var binders: Set<@JvmSuppressWildcards MeterBinder>

  @Inject
  fun initialize() {
    filters.forEach { registry.config().meterFilter(it) }
    binders.forEach { it.bindTo(registry) }
  }
}
