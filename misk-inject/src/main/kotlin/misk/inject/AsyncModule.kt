package misk.inject

import com.google.inject.Binder
import com.google.inject.Injector
import com.google.inject.Module
import com.google.inject.Provider
import jakarta.inject.Inject
import misk.annotation.ExperimentalMiskApi
import kotlin.reflect.KClass

/**
 * This class should be extended by modules that want to contribute tasks to which involve async processing.
 * For example, background jobs, job queues, eventing, message pub/sub.
 *
 * At service build time, these modules can be optionally filtered out before the Guice injector is created,
 * in cases where async processing is not desired, such as in separated main and jobs deployments.
 */
interface AsyncModule
//  : Module
{
  @ExperimentalMiskApi
  @Deprecated("Use moduleWhenDisabled instead.", ReplaceWith("moduleWhenDisabled()"))
  fun moduleWhenAsyncDisabled(): KAbstractModule? = null

//  /** Unique identifier for the callsite or module to allow lookup by feature flag in the [AsyncSwitch]. */
//  @ExperimentalMiskApi
//  fun id(): String = "default"
//
//  /** Class that determines if async functionality is still enabled based on env variable, feature flag or other. */
//  @ExperimentalMiskApi
//  fun switch(): AsyncSwitch
//
//  /**
//   * Returns the module to install when the configuration is enabled (true).
//   */
//  @ExperimentalMiskApi
//  fun moduleWhenEnabled(): KAbstractModule? = null
//
//  /**
//   * Returns the module to install when the configuration is disabled (false).
//   */
//  @ExperimentalMiskApi
//  fun moduleWhenDisabled(): KAbstractModule? = null
//
//  @OptIn(ExperimentalMiskApi::class)
//  override fun configure(binder: Binder) {
//    val isEnabled = switch().isEnabled(id())
//    val selectedModule = if (isEnabled) {
//      moduleWhenEnabled()
//    } else {
//      moduleWhenDisabled()
//    }
//
//    requireNotNull(selectedModule) {
//      "Can't install null module when ${this::class.simpleName} has check isEnabled=${isEnabled}"
//    }
//
//    binder.install(selectedModule)
//  }
}

interface Switch {
  fun isEnabled(key: String): Boolean
}

class AlwaysOnSwitch: Switch {
  override fun isEnabled(key: String) = true
}

//inline fun <reified S : Switch, reified Output : Any, reified Input : Any, reified Enabled : Input, reified Disabled : Input> ConditionalProvider(
//  id: String,
//  noinline transformer: (Input) -> Output = { it as Output },
//) = ConditionalProvider2(id, S::class, Output::class, Input::class, Enabled::class, Disabled::class, transformer)

class ConditionalProvider2<S : Switch, Output : Any, Input : Any>(
  val switchKey: String,
  val switchType: KClass<out S>,
  val outputType: KClass<out Output>,
  val type: KClass<out Input>,
  val enabledInstance: Input,
  val disabledInstance: Input,
  val transformer: (Input) -> Output = { it as Output },
) : Provider<Output> {
  @com.google.inject.Inject(optional = true) var switch: S? = null
  @Inject lateinit var injector: Injector

  override fun get(): Output? {
    val resolved = switch ?: AlwaysOnSwitch()
//    {
//      logger.warn("Switch $switchType not found for $switchKey, defaulting to always enabled.")
//      AlwaysOnSwitch()
//    }

    return if (resolved.isEnabled(switchKey)) {
      transformer(enabledInstance)
    } else {
      transformer(disabledInstance)
    }
  }

  companion object {
    private val logger = misk.logging.getLogger<ConditionalProvider<*, *, *, *, *>>()
  }
}


inline fun <reified S : Switch, reified Output : Any, reified Input : Any, reified Enabled : Input, reified Disabled : Input> ConditionalProvider(
  id: String,
  noinline transformer: (Input) -> Output = { it as Output },
) = ConditionalProvider(id, S::class, Output::class, Input::class, Enabled::class, Disabled::class, transformer)

class ConditionalProvider<S : Switch, Output : Any, Input : Any, Enabled : Input, Disabled : Input>(
  val switchKey: String,
  val switchType: KClass<out S>,
  val outputType: KClass<out Output>,
  val type: KClass<out Input>,
  val enabledType: KClass<out Enabled>,
  val disabledType: KClass<out Disabled>,
  val transformer: (Input) -> Output = { it as Output },
) : Provider<Output> {
  @com.google.inject.Inject(optional = true) var switch: S? = null
  @Inject lateinit var injector: Injector
//  @Inject lateinit var enabled: Enabled
//  @Inject lateinit var disabled: Disabled

  override fun get(): Output? {
    val resolved = switch ?: AlwaysOnSwitch()
//    {
//      logger.warn("Switch $switchType not found for $switchKey, defaulting to always enabled.")
//      AlwaysOnSwitch()
//    }

    val enabled = injector.getInstance(enabledType.java)
    val disabled = injector.getInstance(disabledType.java)

    return if (resolved.isEnabled(switchKey)) {
      transformer(enabled)
    } else {
      transformer(disabled)
    }
  }

  companion object {
    private val logger = misk.logging.getLogger<ConditionalProvider<*, *, *, *, *>>()
  }
}

interface AsyncSwitch: Switch {
}

interface AsyncModule2: Module {
  
}
