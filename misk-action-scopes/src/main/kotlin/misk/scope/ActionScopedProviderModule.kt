package misk.scope

import com.google.inject.Key
import com.google.inject.Provider
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import com.google.inject.multibindings.Multibinder
import com.squareup.moshi.Types
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.parameterizedType
import misk.inject.toKey
import misk.inject.typeLiteral
import java.lang.reflect.Type
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlin.reflect.jvm.javaMethod

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

  /** Binds an [ActionScoped] which only pulls from data seeded at the scope entry */
  fun <T : Any> bindSeedData(type: TypeLiteral<T>) {
    bindSeedData(
      type.toKey(),
      Key.get(Types.newParameterizedType(ActionScoped::class.java, type.type)) as Key<ActionScoped<T>>,
    )
  }

  /** Binds an annotation qualified [ActionScoped] which only pulls from data seeded at the scope entry */
  fun <T : Any> bindSeedData(kclass: KClass<T>, a: Annotation) {
    bindSeedData(Key.get(kclass.java, a), Key.get(actionScopedType(kclass), a))
  }

  /** Binds an annotation qualified [ActionScoped] which only pulls from data seeded at the scope entry */
  fun <T : Any> bindSeedData(type: TypeLiteral<T>, a: Annotation) {
    bindSeedData(
      type.toKey(),
      Key.get(Types.newParameterizedType(ActionScoped::class.java, type.type), a) as Key<ActionScoped<T>>,
    )
  }

  /** Binds an annotation qualified [ActionScoped] which only pulls from data seeded at the scope entry */
  fun <T : Any, A : Annotation> bindSeedData(kclass: KClass<T>, a: KClass<A>) {
    bindSeedData(Key.get(kclass.java, a.java), Key.get(actionScopedType(kclass), a.java))
  }

  private fun <T : Any> bindSeedData(key: Key<T>, actionScopedKey: Key<ActionScoped<T>>) {
    bindProvider(
      key, actionScopedKey,
      Provider<ActionScopedProvider<T>> {
        SeedDataActionScopedProvider(key)
      }
    )
  }

  /** Binds an annotation qualified [ActionScoped] along with its provider */
  fun <T : Any> bindProvider(
    kclass: KClass<T>,
    providerType: KClass<out ActionScopedProvider<T>>,
    annotatedBy: Annotation? = null
  ) {
    val typeKey =
      if (annotatedBy == null) Key.get(kclass.java)
      else Key.get(kclass.java, annotatedBy)

    val actionScopedType = actionScopedType(kclass.java)
    val actionScopedKey =
      if (annotatedBy == null) Key.get(actionScopedType)
      else Key.get(actionScopedType, annotatedBy)

    bindProvider(typeKey, actionScopedKey, binder().getProvider(providerType.java))
  }

  /** Binds an annotation qualified [ActionScoped] along with its provider */
  fun <T> bindProvider(
    type: TypeLiteral<T>,
    providerType: KClass<out ActionScopedProvider<T>>,
    annotatedBy: Annotation? = null
  ) {
    val typeKey =
      if (annotatedBy == null) Key.get(type)
      else Key.get(type, annotatedBy)

    @Suppress("UNCHECKED_CAST")
    val actionScopedType = actionScopedType(type.type) as TypeLiteral<ActionScoped<T>>
    val actionScopedKey =
      if (annotatedBy == null) Key.get(actionScopedType)
      else Key.get(actionScopedType, annotatedBy)

    bindProvider(typeKey, actionScopedKey, binder().getProvider(providerType.java))
  }

  /** Binds an annotation qualified [ActionScoped] along with its provider */
  fun <T : Any, A : Annotation> bindProvider(
    kclass: KClass<T>,
    providerType: KClass<out ActionScopedProvider<T>>,
    annotatedBy: Class<A>
  ) {
    val typeKey = Key.get(kclass.java, annotatedBy)
    val actionScopedType = actionScopedType(kclass.java)
    val actionScopedKey = Key.get(actionScopedType, annotatedBy)
    bindProvider(typeKey, actionScopedKey, binder().getProvider(providerType.java))
  }

  /** Binds an annotation qualified [ActionScoped] along with its provider */
  fun <T, A : Annotation> bindProvider(
    type: TypeLiteral<T>,
    providerType: KClass<out ActionScopedProvider<T>>,
    annotatedBy: Class<A>
  ) {
    val typeKey = Key.get(type, annotatedBy)

    @Suppress("UNCHECKED_CAST")
    val actionScopedType = actionScopedType(type.type) as TypeLiteral<ActionScoped<T>>
    val actionScopedKey = Key.get(actionScopedType, annotatedBy)
    bindProvider(typeKey, actionScopedKey, binder().getProvider(providerType.java))
  }

  private fun <T> bindProvider(
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
    }).asSingleton()
    bind(actionScopedKey.withWildcard()).to(actionScopedKey)
  }

  /**
   * Given a key like `ActionScoped<Runnable>` this returns a Key like `ActionScoped<out Runnable>`.
   * It's necessary because Kotlin gives us wildcards we don't want.
   */
  private fun <T> Key<ActionScoped<T>>.withWildcard(): Key<ActionScoped<T>> {
    val t = typeLiteral.getReturnType(ActionScoped<*>::get.javaMethod).type
    val outT = Types.subtypeOf(t)
    val actionScopedOfOutT = Types.newParameterizedType(ActionScoped::class.java, outT)
    @Suppress("UNCHECKED_CAST") // We do runtime checks to confirm this is safe.
    return ofType(actionScopedOfOutT) as Key<ActionScoped<T>>
  }

  companion object {
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> actionScopedType(kclass: KClass<T>) = actionScopedType(kclass.java)

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> actionScopedType(clazz: Class<T>) =
      parameterizedType<ActionScoped<T>>(clazz).typeLiteral() as TypeLiteral<ActionScoped<T>>

    @Suppress("UNCHECKED_CAST")
    private fun actionScopedType(type: Type) =
      parameterizedType<ActionScoped<Any>>(type).typeLiteral() as TypeLiteral<ActionScoped<Any>>

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
