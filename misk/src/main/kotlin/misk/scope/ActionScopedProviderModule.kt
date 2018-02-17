package misk.scope

import com.google.inject.Key
import com.google.inject.Provider
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import com.google.inject.multibindings.Multibinder
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.parameterizedType
import misk.inject.typeLiteral
import javax.inject.Inject
import kotlin.reflect.KClass

/** Module used by components and applications to provide [ActionScoped] context objects */
abstract class ActionScopedProviderModule : KAbstractModule() {
  override fun configure() {
    MapBinder.newMapBinder(binder(), KEY_TYPE, ACTION_SCOPED_PROVIDER_TYPE)
    Multibinder.newSetBinder(binder(), KEY_TYPE)
    configureProviders()
  }

  abstract fun configureProviders()

  /** Binds an [ActionScoped] which only pulls from data seeded at the scope entry */
  fun <T : Any> bindSeedData(kclass: KClass<T>) {
    bindSeedData(Key.get(kclass.java), Key.get(actionScopedType(kclass)))
  }

  /** Binds an annotation qualified [ActionScoped] which only pulls from data seeded at the scope entry */
  fun <T : Any> bindSeedData(
      kclass: KClass<T>,
      a: Annotation
  ) {
    bindSeedData(Key.get(kclass.java, a), Key.get(actionScopedType(kclass), a))
  }

  /** Binds an annotation qualified [ActionScoped] which only pulls from data seeded at the scope entry */
  fun <T : Any, A : Annotation> bindSeedData(
      kclass: KClass<T>,
      a: KClass<A>
  ) {
    bindSeedData(Key.get(kclass.java, a.java), Key.get(actionScopedType(kclass), a.java))
  }

  private fun <T : Any> bindSeedData(
      key: Key<T>,
      actionScopedKey: Key<ActionScoped<T>>
  ) {
    bindProvider(key, actionScopedKey, Provider<ActionScopedProvider<T>> {
      SeedDataActionScopedProvider(key)
    })
  }

  /** Binds an unqualified [ActionScoped] along with its provider */
  fun <T : Any> bindProvider(
      kclass: KClass<T>,
      providerClass: KClass<out ActionScopedProvider<T>>
  ) {
    bindProvider(
        Key.get(kclass.java),
        Key.get(actionScopedType(kclass)),
        binder().getProvider(providerClass.java)
    )
  }

  /** Binds an annotation qualified [ActionScoped] along with its provider */
  fun <T : Any> bindProvider(
      kclass: KClass<T>,
      a: Annotation,
      providerClass: KClass<out ActionScopedProvider<T>>
  ) {
    bindProvider(
        Key.get(kclass.java, a),
        Key.get(actionScopedType(kclass), a),
        binder().getProvider(providerClass.java)
    )
  }

  /** Binds an annotation qualified [ActionScoped] along with its provider */
  fun <T : Any> bindProvider(
      kclass: KClass<T>,
      a: KClass<Annotation>,
      providerClass: KClass<out ActionScopedProvider<T>>
  ) {
    bindProvider(
        Key.get(kclass.java, a.java),
        Key.get(actionScopedType(kclass), a.java),
        binder().getProvider(providerClass.java)
    )
  }

  private fun <T : Any> bindProvider(
      key: Key<T>,
      actionScopedKey: Key<ActionScoped<T>>,
      providerProvider: Provider<out ActionScopedProvider<T>>
  ) {
    MapBinder.newMapBinder(binder(), KEY_TYPE, ACTION_SCOPED_PROVIDER_TYPE)
        .addBinding(key)
        .toProvider(providerProvider)
    bind(actionScopedKey).toProvider(object : Provider<ActionScoped<T>> {
      @Inject lateinit var scope: ActionScope
      override fun get() = RealActionScoped(key, scope)
    })
        .asSingleton()
  }

  companion object {
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> actionScopedType(kclass: KClass<T>) =
        parameterizedType<ActionScoped<T>>(kclass.java).typeLiteral()
            as TypeLiteral<ActionScoped<T>>

    private val KEY_TYPE = object : TypeLiteral<Key<*>>() {}
    private val ACTION_SCOPED_PROVIDER_TYPE = object : TypeLiteral<ActionScopedProvider<*>>() {}

    private class SeedDataActionScopedProvider<out T>(private val key: Key<T>) :
        ActionScopedProvider<T> {
      override fun get(): T {
        throw IllegalStateException("$key can only be provided as seed data")
      }
    }
  }

}
