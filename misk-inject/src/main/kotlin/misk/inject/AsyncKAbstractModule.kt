package misk.inject

import misk.annotation.ExperimentalMiskApi

/**
 * This class should be extended by modules that want to contribute tasks to which involve async processing.
 * For example, background jobs, job queues, eventing, message pub/sub.
 *
 * At service build time, these modules can be optionally filtered out before the Guice injector is created,
 * in cases where async processing is not desired, such as in separated main and jobs deployments.
 */
open class AsyncKAbstractModule : KAbstractModule() {
  /**
   * Returns a module that would be installed when async tasks are disabled.
   * By default, this is a module that calls [configureWhenAsyncDisabled].
   * Subclasses can override this method to provide a different module if needed.
   */
  @ExperimentalMiskApi
  open fun moduleWhenAsyncDisabled(): KAbstractModule? = null
}