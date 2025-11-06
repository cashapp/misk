package misk.inject

import com.google.inject.Binder
import com.google.inject.Module
import misk.annotation.ExperimentalMiskApi

/**
 * This class should be extended by modules that want to contribute tasks to which involve async processing.
 * For example, background jobs, job queues, eventing, message pub/sub.
 *
 * At service build time, these modules can be optionally filtered out before the Guice injector is created,
 * in cases where async processing is not desired, such as in separated main and jobs deployments.
 */
interface AsyncModule: Module {
  @ExperimentalMiskApi
  @Deprecated("Use moduleWhenDisabled instead.", ReplaceWith("moduleWhenDisabled()"))
  fun moduleWhenAsyncDisabled(): KAbstractModule? = null

  /** Unique identifier for the callsite or module to allow lookup by feature flag in the [AsyncSwitch]. */
  @ExperimentalMiskApi
  fun id(): String = "default"

  /** Class that determines if async functionality is still enabled based on env variable, feature flag or other. */
  @ExperimentalMiskApi
  fun switch(): AsyncSwitch

  /**
   * Returns the module to install when the configuration is enabled (true).
   */
  @ExperimentalMiskApi
  fun moduleWhenEnabled(): KAbstractModule

  /**
   * Returns the module to install when the configuration is disabled (false).
   */
  @ExperimentalMiskApi
  fun moduleWhenDisabled(): KAbstractModule? = null

  @OptIn(ExperimentalMiskApi::class)
  override fun configure(binder: Binder) {
    val isEnabled = switch().isEnabled(id())
    val selectedModule = if (isEnabled) {
      moduleWhenEnabled()
    } else {
      moduleWhenDisabled()
    }

    requireNotNull(selectedModule) {
      "Can't install null module when ${this::class.simpleName} has check isEnabled=${isEnabled}"
    }

    binder.install(selectedModule)
  }
}

interface Switch {
  fun isEnabled(id: String): Boolean
}

interface AsyncSwitch: Switch {
}

interface AsyncModule2: Module {
  
}
