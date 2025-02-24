package misk.warmup

import misk.annotation.ExperimentalMiskApi

/**
 * @see WarmupTask
 */
@ExperimentalMiskApi
abstract class SuspendingWarmupTask : WarmupTask() {
  override fun execute() {
    throw UnsupportedOperationException("Suspending warmup task should implement only executeSuspending method")
  }

  /**
   * Perform production-like work to cause caches to be seeded, pools to be filled, and hot spots to
   * be compiled. This should return once warmup is complete.
   *
   * This function will be executed on a dedicated warmup thread.
   */
  abstract suspend fun executeSuspending()
}
