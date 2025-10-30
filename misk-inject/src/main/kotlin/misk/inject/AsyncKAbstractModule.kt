package misk.inject

import misk.annotation.ExperimentalMiskApi

/**
 * This class should be extended by modules that want to contribute tasks to which involve async processing.
 * For example, background jobs, job queues, eventing, message pub/sub.
 *
 * At service build time, these modules can be optionally filtered out before the Guice injector is created,
 * in cases where async processing is not desired, such as in separated main and jobs deployments.
 */
@Deprecated(
  message = "Extend both AsyncModule interface and KAbstractModule() or KInstallOnceModule() directly.",
)
open class AsyncKAbstractModule : AsyncModule, KAbstractModule() {
  /**
   * Returns a module that would be installed when async tasks are disabled.
   * By default, this is a module that calls [configureWhenAsyncDisabled].
   * Subclasses can override this method to provide a different module if needed.
   */
  @ExperimentalMiskApi
  override fun moduleWhenAsyncDisabled(): KAbstractModule? = null
}

/**
 * This class should be extended by modules that want to contribute tasks to which involve async processing.
 * For example, background jobs, job queues, eventing, message pub/sub.
 *
 * At service build time, these modules can be optionally filtered out before the Guice injector is created,
 * in cases where async processing is not desired, such as in separated main and jobs deployments.
 */
interface AsyncModule {
  @ExperimentalMiskApi
  fun moduleWhenAsyncDisabled(): KAbstractModule? = null
}
