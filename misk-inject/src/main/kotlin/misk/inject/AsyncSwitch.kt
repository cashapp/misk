package misk.inject

import com.google.inject.Injector
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
@Deprecated(
  message = "Use AsyncSwitch directly or conditionalOn ServiceModule or ConditionalProvider.",
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
 * This interface should be implemented by modules that want to contribute tasks which involve async processing. For
 * example, background jobs, job queues, eventing, message pub/sub.
 *
 * At service build time, these modules can be optionally filtered out before the Guice injector is created, in cases
 * where async processing is not desired, such as in separated main and jobs deployments.
 */
@Deprecated("Use AsyncSwitch directly or conditionalOn ServiceModule or ConditionalProvider.")
interface AsyncModule {
  @ExperimentalMiskApi
  @Deprecated("Use AsyncSwitch directly or conditionalOn ServiceModule or ConditionalProvider.")
  fun moduleWhenAsyncDisabled(): KAbstractModule? = null
}

/** A simple abstraction which can be used to enable or disable parts of the dependency injection graph. */
interface Switch {
  fun isEnabled(key: String): Boolean

  fun isDisabled(key: String): Boolean = !isEnabled(key)

  fun <T> ifEnabled(key: String, block: () -> T) =
    if (isEnabled(key)) {
      block()
    } else Unit

  fun <T> ifDisabled(key: String, block: () -> T) =
    if (isDisabled(key)) {
      block()
    } else Unit
}

/** Holder interface for a switch which enables async processing within the application pod. */
interface AsyncSwitch : Switch

/** Default switch which always enables async processing within the application pod. */
class AlwaysEnabledSwitch @Inject constructor() : Switch, AsyncSwitch {
  override fun isEnabled(key: String) = true
}

/** Module which configures a default [AsyncSwitch] to be always enabled if no other [AsyncSwitch] is bound. */
class DefaultAsyncSwitchModule : KInstallOnceModule() {
  override fun configure() {
    bindOptionalDefault<AsyncSwitch>().to<AlwaysEnabledSwitch>()
  }
}

inline fun <
  reified S : Switch,
  reified Output : Any,
  reified Input,
  reified Enabled : Input,
  reified Disabled : Input,
  > ConditionalProvider(
    switchKey: String,
    enabledInstance: Enabled,
    disabledInstance: Disabled,
    noinline transformer: (Input) -> Output = { it as Output }
  ) = ConditionalProvider(
    switchKey = switchKey,
    switchType = S::class,
    outputType = Output::class,
    type = Any::class,
    enabledInstance = enabledInstance,
    disabledInstance = disabledInstance,
    transformer = transformer as (Any?) -> Output,
  )

class ConditionalProvider<S : Switch, Output : Any, Input> @JvmOverloads constructor(
  val switchKey: String,
  val switchType: KClass<out S>,
  val outputType: KClass<out Output>,
  val type: KClass<*>,
  val enabledInstance: Input,
  val disabledInstance: Input,
  val transformer: (Input) -> Output = { it as Output },
) : Provider<Output> {
  @Inject
  lateinit var injector: Injector

  override fun get(): Output {
    val switch = injector.getInstance(switchType.java)
    return if (switch.isEnabled(switchKey)) {
      transformer(enabledInstance)
    } else {
      transformer(disabledInstance)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ConditionalProvider<*, *, *>) return false
    if (switchKey != other.switchKey) return false
    if (switchType != other.switchType) return false
    if (outputType != other.outputType) return false
    if (type != other.type) return false
    if (enabledInstance != other.enabledInstance) return false
    if (disabledInstance != other.disabledInstance) return false
    return true
  }

  override fun hashCode(): Int {
    var result = switchKey.hashCode()
    result = 31 * result + switchType.hashCode()
    result = 31 * result + outputType.hashCode()
    result = 31 * result + type.hashCode()
    result = 31 * result + (enabledInstance?.hashCode() ?: 0)
    result = 31 * result + (disabledInstance?.hashCode() ?: 0)
    return result
  }
}

inline fun <
  reified S : Switch,
  reified Output : Any,
  reified Input : Any,
  reified Enabled : Input,
  reified Disabled : Input,
  > ConditionalTypedProvider(switchKey: String, noinline transformer: (Input) -> Output = { it as Output }) =
  ConditionalTypedProvider(
    switchKey,
    S::class,
    Output::class,
    Input::class,
    Enabled::class,
    Disabled::class,
    transformer
  )

inline fun <
  reified S : Switch,
  reified Output : Any,
  reified Enabled : Output,
  reified Disabled : Output,
  > ConditionalTypedProvider(switchKey: String) =
  ConditionalTypedProvider(
    switchKey,
    S::class,
    Output::class,
    Output::class,
    Enabled::class,
    Disabled::class,
  )

class ConditionalTypedProvider<S : Switch, Output : Any, Input : Any, Enabled : Input, Disabled : Input> @JvmOverloads constructor(
  val switchKey: String,
  val switchType: KClass<out S>,
  val outputType: KClass<out Output>,
  val type: KClass<out Input>,
  val enabledType: KClass<out Enabled>,
  val disabledType: KClass<out Disabled>,
  val transformer: (Input) -> Output = { it as Output },
) : Provider<Output> {
  @Inject
  lateinit var injector: Injector

  override fun get(): Output {
    val switch = injector.getInstance(switchType.java)
    val enabled = injector.getInstance(enabledType.java)
    val disabled = injector.getInstance(disabledType.java)

    return if (switch.isEnabled(switchKey)) {
      transformer(enabled)
    } else {
      transformer(disabled)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ConditionalTypedProvider<*, *, *, *, *>) return false
    if (switchKey != other.switchKey) return false
    if (switchType != other.switchType) return false
    if (outputType != other.outputType) return false
    if (type != other.type) return false
    if (enabledType != other.enabledType) return false
    if (disabledType != other.disabledType) return false
    return true
  }

  override fun hashCode(): Int {
    var result = switchKey.hashCode()
    result = 31 * result + switchType.hashCode()
    result = 31 * result + outputType.hashCode()
    result = 31 * result + type.hashCode()
    result = 31 * result + enabledType.hashCode()
    result = 31 * result + disabledType.hashCode()
    return result
  }
}