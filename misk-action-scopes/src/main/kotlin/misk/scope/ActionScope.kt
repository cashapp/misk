package misk.scope

import com.google.inject.Key
import com.google.inject.Provider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asContextElement
import java.util.concurrent.Callable
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVisibility

@Singleton
class ActionScope @Inject internal constructor(
  // NB(mmihic): ActionScoped depends on ActionScope depends on
  // on ActionScopedProviders, which might depend on other ActionScopeds. We break
  // this circular dependency by injecting a map of Provider<ActionScopedProvider>
  // rather than the map of ActionScopedProvider directly
  private val providers: @JvmSuppressWildcards Map<Key<*>, Provider<ActionScopedProvider<*>>>
) : AutoCloseable {
  companion object {
    private val threadLocalInstance = ThreadLocal<Instance>()
  }

  /**
   * Wraps a [kotlinx.coroutines.runBlocking] to propagate the current action scope.
   */
  @Deprecated(
    message = "don't use runBlocking explicitly",
    replaceWith = ReplaceWith("use suspending invocation")
  )
  fun <T> runBlocking(block: suspend CoroutineScope.() -> T): T {
    return if (inScope()) {
      kotlinx.coroutines.runBlocking(asContextElement(), block)
    } else {
      kotlinx.coroutines.runBlocking {
        block()
      }
    }
  }

  /**
   * Wraps a [kotlinx.coroutines.runBlocking] to propagate the current action scope.
   */
  @Deprecated(
    message = "don't use runBlocking explicitly",
    replaceWith = ReplaceWith("use suspending invocation")
  )
  fun <T> runBlocking(context: CoroutineContext, block: suspend CoroutineScope.() -> T): T {
    return if (inScope()) {
      kotlinx.coroutines.runBlocking(context + asContextElement(), block)
    } else {
      kotlinx.coroutines.runBlocking(context, block)
    }
  }

  /**
   * Converts the action scope into a [CoroutineContext.Element] to maintain the given ActionScope context
   * for coroutines regardless of the actual thread they run on.
   *
   * Example usage:
   * ```
   *  scope.enter(seedData).use {
   *    runBlocking(scope.asContextElement()) {
   *      async(Dispatchers.IO) {
   *        tester.fooValue()
   *      }.await()
   *    }
   *  }
   * ```
   *
   */
  fun asContextElement(): CoroutineContext.Element {
    check(inScope()) { "not running within an ActionScope" }

    val instance = threadLocalInstance.get()

    return threadLocalInstance.asContextElement(instance)
  }

  @Deprecated(
    "Use snapshotActionScopeInstance instead",
    ReplaceWith("this.snapshotActionScopeInstance()"),
  )
  fun snapshotActionScope(): Map<Key<*>, Any?> {
    return snapshotActionScopeInstance().asImmediateValues()
  }

  fun snapshotActionScopeInstance(): Instance {
    check(inScope()) { "not running within an ActionScope" }
    return threadLocalInstance.get()
  }

  @Deprecated("Use create() instead and then call inScope() to enter the scope")
    /** Starts the scope on a thread with the provided seed data */
  fun enter(seedData: Map<Key<*>, Any?>): ActionScope {
    create(seedData).enter()
    return this
  }

  /** Creates a new scope on the current thread with the provided seed data */
  @JvmOverloads
 fun create(
    seedData: Map<Key<*>, Any?>,
    providerOverrides: Map<Key<*>, ActionScopedProvider<*>> = emptyMap(),
  ): Instance {
    check(!inScope()) {
      "cannot create an ActionScope.Instance on a thread that is already running in an action scope"
    }

    val immediateValues = seedData.mapValues { (_, value) -> ImmediateLazy(value) }

    val lazyValues = providers.mapValues { (key, _) ->
      SynchronizedLazy(providerFor(key))
    }

    val lazyOverrides = providerOverrides.mapValues { (_, provider) ->
      SynchronizedLazy(provider)
    }

    return Instance(lazyValues + lazyOverrides + immediateValues, this)
  }

  /** Starts the scope on a thread with the provided instance */
  internal fun enter(instance: Instance): ActionScope {
    check(!inScope()) {
      "cannot begin an ActionScope on a thread that is already running in an action scope"
    }

    threadLocalInstance.set(instance)

    return this
  }

  override fun close() {
    threadLocalInstance.remove()
  }

  /** Returns true if currently in the scope */
  fun inScope(): Boolean = threadLocalInstance.get() != null

  /**
   * Wraps a [Callable] that will be called on another thread, propagating the current
   * scoped data onto that thread
   */
  fun <T> propagate(c: Callable<T>): Callable<T> {
    check(inScope()) { "not running within an ActionScope" }

    val currentInstance = threadLocalInstance.get()

    return Callable {
      // If the original scope is the same as the scope that calls the KFunction and we are already
      // in scope, then there is no need to re-enter the scope.
      if (inScope() && currentInstance === threadLocalInstance.get()) {
        c.call()
      } else {
        currentInstance.inScope {
          c.call()
        }
      }
    }
  }

  /**
   * Wraps a [KFunction] that will be called on another thread, propagating the current
   * scoped data onto that thread
   */
  fun <T> propagate(f: KFunction<T>): KFunction<T> {
    check(inScope()) { "not running within an ActionScope" }

    val currentInstance = threadLocalInstance.get()
    return WrappedKFunction(currentInstance, this, f)
  }

  /**
   * Wraps a function or lambda that will be called on another thread, propagating the current
   * scoped data onto that thread
   */
  fun <T> propagate(f: () -> T): () -> T {
    check(inScope()) { "not running within an ActionScope" }

    val currentInstance = threadLocalInstance.get()

    return {
      // If the original scope is the same as the scope that calls the KFunction and we are already
      // in scope, then there is no need to re-enter the scope.
      if (inScope() && currentInstance === threadLocalInstance.get()) {
        f.invoke()
      } else {
        currentInstance.inScope(f)
      }
    }
  }

  /** Returns the action scoped value for the given key */
  fun <T> get(key: Key<T>): T {
    check(inScope()) { "not running within an ActionScope" }
    return threadLocalInstance.get()[key]
  }

  private fun providerFor(key: Key<*>): ActionScopedProvider<*> {
    return requireNotNull(providers[key]?.get()) {
      "no ActionScopedProvider available for $key"
    }
  }

  class Instance internal constructor(
    private val lazyValues: Map<Key<*>, Lazy<*>>,
    private val scope: ActionScope,
  ) : AutoCloseable by scope {
    internal operator fun <T> get(key: Key<T>): T {
      @Suppress("UNCHECKED_CAST")
      return lazyValues.getValue(key).value as T
    }

    internal fun asImmediateValues(): Map<Key<*>, Any?> {
      return lazyValues
        .filterValues { it.isInitialized() }
        .mapValues { it.value.value }
    }

    fun <T> inScope(block: () -> T): T {
      return scope.enter(this).use {
        block()
      }
    }

    fun enter() {
      scope.enter(this)
    }
  }

  private class WrappedKFunction<T>(
    val instance: Instance,
    val scope: ActionScope,
    val wrapped: KFunction<T>,
  ) : KFunction<T> {
    override fun call(vararg args: Any?): T {
      // If the original scope is the same as the scope that calls the KFunction and we are already
      // in scope, then there is no need to re-enter the scope.
      return if (scope.inScope() && instance === threadLocalInstance.get()) {
        wrapped.call(*args)
      } else {
        instance.inScope {
          wrapped.call(*args)
        }
      }
    }

    override fun callBy(args: Map<KParameter, Any?>): T {
      // If the original scope is the same as the scope that calls the KFunction and we are already
      // in scope, then there is no need to re-enter the scope.
      return if (scope.inScope() && instance === threadLocalInstance.get()) {
        wrapped.callBy(args)
      } else {
        instance.inScope {
          wrapped.callBy(args)
        }
      }
    }

    override val annotations: List<Annotation> = wrapped.annotations
    override val isAbstract: Boolean = wrapped.isAbstract
    override val isFinal: Boolean = true
    override val isOpen: Boolean = false
    override val name: String = wrapped.name
    override val parameters: List<KParameter> = wrapped.parameters
    override val returnType: KType = wrapped.returnType
    override val typeParameters: List<KTypeParameter> = wrapped.typeParameters
    override val visibility: KVisibility? = wrapped.visibility
    override val isExternal: Boolean = wrapped.isExternal
    override val isInfix: Boolean = wrapped.isInfix
    override val isInline: Boolean = wrapped.isInline
    override val isOperator: Boolean = wrapped.isOperator
    override val isSuspend: Boolean = wrapped.isSuspend
  }
}
