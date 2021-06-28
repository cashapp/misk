package misk.warmup

/**
 * Register a warmup task in your service with the following:
 *
 * ```
 * install(WarmupModule<MyWarmupTask>())
 * ```
 *
 * Misk will run the task **after** all services have started successfully, but **before** health
 * checks report the service as healthy. Warm up tasks should complete quickly (under 10 seconds is
 * best) because the service won't serve live traffic until all warm up tasks complete.
 */
abstract class WarmupTask {
  /**
   * Perform production-like work to cause caches to be seeded, pools to be filled, and hot spots to
   * be compiled. This should return once warmup is complete.
   */
  abstract fun execute()
}
