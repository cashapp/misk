package misk.scope

import com.google.inject.Key
import com.google.inject.Provider
import java.util.concurrent.Callable
import javax.inject.Inject
import javax.inject.Singleton
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
    private val tls = ThreadLocal<LinkedHashMap<Key<*>, Any?>>()
  }

  /** Starts the scope on a thread with the provided seed data */
  fun enter(seedData: Map<Key<*>, Any?>): ActionScope {
    check(tls.get() == null) {
      "cannot begin an ActionScope on a thread that is already running in an action scope"
    }

    tls.set(LinkedHashMap(seedData))
    return this
  }

  override fun close() {
    tls.remove()
  }

  /** Returns true if currently in the scope */
  fun inScope(): Boolean = tls.get() != null

  /**
   * Wraps a [Callable] that will be called on another thread, propagating the current
   * scoped data onto that thread
   */
  fun <T> propagate(c: Callable<T>): Callable<T> {
    check(tls.get() != null) { "not running within an ActionScope" }

    val currentScopedData = tls.get().toMap()
    return Callable {
      enter(currentScopedData).use {
        c.call()
      }
    }
  }

  /**
   * Wraps a [KFunction] that will be called on another thread, propagating the current
   * scoped data onto that thread
   */
  fun <T> propagate(f: KFunction<T>): KFunction<T> {
    check(tls.get() != null) { "not running within an ActionScope" }

    val currentScopedData = tls.get().toMap()
    return WrappedKFunction(currentScopedData, this, f)
  }

  /**
   * Wraps a function or lambda that will be called on another thread, propagating the current
   * scoped data onto that thread
   */
  fun <T> propagate(f: () -> T): () -> T {
    check(tls.get() != null) { "not running within an ActionScope" }

    val currentScopedData = tls.get().toMap()
    return {
      enter(currentScopedData).use {
        f.invoke()
      }
    }
  }

  /** Returns the action scoped value for the given key */
  fun <T> get(key: Key<T>): T {
    check(tls.get() != null) { "not running within an ActionScope" }

    // NB(mmihic): We don't use computeIfAbsent because computing the value of this
    // key might require computing the values of keys on which we depend, which would
    // cause recursive calls to computeIfAbsent which is unsupported (and explicitly
    // detected in JDK 9+)
    val threadState = tls.get()
    val cachedValue = threadState[key]
    if (cachedValue != null) {
      @Suppress("UNCHECKED_CAST")
      return cachedValue as T
    }

    val value = providerFor(key as Key<*>).get()
    threadState[key] = value

    @Suppress("UNCHECKED_CAST")
    return value as T
  }

  @Suppress("UNCHECKED_CAST")
  private fun providerFor(key: Key<*>): ActionScopedProvider<*> {
    return requireNotNull(providers[key]?.get()) {
      "no ActionScopedProvider available for $key"
    }
  }

  private class WrappedKFunction<T>(
    val seedData: Map<Key<*>, Any?>,
    val scope: ActionScope,
    val wrapped: KFunction<T>
  ) : KFunction<T> {
    override fun call(vararg args: Any?): T = scope.enter(seedData).use {
      wrapped.call(*args)
    }

    override fun callBy(args: Map<KParameter, Any?>): T = scope.enter(seedData).use {
      wrapped.callBy(args)
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
