package misk.inject

import com.google.inject.AbstractModule
import com.google.inject.Binder
import com.google.inject.Key
import com.google.inject.TypeLiteral
import com.google.inject.binder.AnnotatedBindingBuilder
import com.google.inject.binder.LinkedBindingBuilder
import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.multibindings.MapBinder
import com.google.inject.multibindings.Multibinder
import com.google.inject.util.Types
import java.lang.reflect.Type
import kotlin.reflect.KClass

/**
 * A class that provides helper methods for working with Kotlin and Guice, allowing implementing
 * classes to operate in the Kotlin type system rather than converting to Java.
 *
 * The more Kotlin friendly API allows calls like:
 *
 * ```
 * bind(Foo::class.java).to(RealFoo::class.java)
 * ```
 *
 * To be rewritten as:
 * ```
 * bind<Foo>().to<RealFoo>()
 * ```
 */
abstract class KAbstractModule : AbstractModule() {
  protected class KotlinAnnotatedBindingBuilder<X>(
    private val annotatedBuilder: AnnotatedBindingBuilder<X>
  ) : AnnotatedBindingBuilder<X> by annotatedBuilder {
    inline fun <reified T : Annotation> annotatedWith(): LinkedBindingBuilder<X> {
      return annotatedWith(T::class.java)
    }
  }

  protected inline fun <reified T : Any> bind(): KotlinAnnotatedBindingBuilder<in T> {
    return KotlinAnnotatedBindingBuilder<T>(binder().bind(T::class.java))
  }

  protected inline fun <reified T : Any> LinkedBindingBuilder<in T>.to(): ScopedBindingBuilder {
    return to(T::class.java)
  }

  protected inline fun <reified T : Any> requireBinding() {
    requireBinding(T::class.java)
  }

  protected inline fun <reified T : Any> multibind(
    annotation: KClass<out Annotation>? = null
  ): LinkedBindingBuilder<T> = newMultibinder<T>(annotation).addBinding()

  protected inline fun <reified T : Any> multibind(
    annotation: Annotation
  ): LinkedBindingBuilder<T> = newMultibinder<T>(annotation).addBinding()

  protected inline fun <reified T : Any, reified A : Annotation> multibind():
    LinkedBindingBuilder<T> = newMultibinder<T>(A::class).addBinding()

  protected inline fun <reified T : Any> newMultibinder(
    annotation: KClass<out Annotation>? = null
  ): Multibinder<T> = newMultibinder(T::class, annotation)

  protected inline fun <reified T : Any> newMultibinder(
    annotation: Annotation
  ): Multibinder<T> = newMultibinder(Key.get(T::class.java, annotation))

  @Suppress("UNCHECKED_CAST")
  protected fun <T : Any> newMultibinder(
    type: KClass<T>,
    annotation: KClass<out Annotation>? = null
  ): Multibinder<T> {
    return when (annotation) {
      null -> newMultibinder(Key.get(type.java))
      else -> newMultibinder(Key.get(type.java, annotation.java))
    }
  }

  @Suppress("UNCHECKED_CAST")
  protected fun <T : Any> newMultibinder(
    key: Key<T>
  ): Multibinder<T> {
    val type = key.typeLiteral.type

    val setOfT = parameterizedType<Set<*>>(type).typeLiteral() as TypeLiteral<Set<T>>
    val mutableSetOfTKey = key.ofType(setOfT) as Key<MutableSet<T>>
    val setOfOutT =
      parameterizedType<Set<*>>(Types.subtypeOf(type.java)).typeLiteral() as TypeLiteral<Set<T>>
    val setOfOutTKey = setOfOutT.toKey(annotation)
    val listOfT = parameterizedType<List<*>>(type.java).typeLiteral() as TypeLiteral<List<T>>
    val listOfOutT =
      parameterizedType<List<*>>(Types.subtypeOf(type.java)).typeLiteral() as TypeLiteral<List<T>>
    val listOfOutTKey = listOfOutT.toKey(annotation)
    val listOfTKey = listOfT.toKey(annotation)
    bind(listOfOutTKey).toProvider(
      ListProvider(mutableSetOfTKey, getProvider(mutableSetOfTKey))
    )
    bind(setOfOutTKey).to(setOfT.toKey(annotation))
    bind(listOfTKey).to(listOfOutTKey)

    return Multibinder.newSetBinder(binder(), key)
  }

  protected inline fun <reified K : Any, reified V : Any> newMapBinder(
    annotation: KClass<out Annotation>? = null
  ): MapBinder<K, V> = newMapBinder(K::class, V::class, annotation)

  protected fun <K : Any, V : Any> newMapBinder(
    keyType: KClass<K>,
    valueType: KClass<V>,
    annotation: KClass<out Annotation>? = null
  ): MapBinder<K, V> = newMapBinder(keyType.typeLiteral(), valueType.typeLiteral(), annotation)

  protected fun <K : Any, V : Any> newMapBinder(
    keyType: TypeLiteral<K>,
    valueType: TypeLiteral<V>,
    annotation: KClass<out Annotation>? = null
  ): MapBinder<K, V> {
    val mapOfKV = mapOfType(keyType, valueType).toKey(annotation)
    val mapOfKOutV = mapOfType<K, V>(keyType.type, valueType.subtype()).toKey(annotation)
    val mapOfOutKV = mapOfType<K, V>(keyType.subtype(), valueType.type).toKey(annotation)
    val mapOfOutKOutV = mapOfType<K, V>(keyType.subtype(), valueType.subtype()).toKey(annotation)

    bind(mapOfKOutV).to(mapOfKV)
    bind(mapOfOutKV).to(mapOfKV)
    bind(mapOfOutKOutV).to(mapOfKV)

    return when (annotation) {
      null -> MapBinder.newMapBinder(binder(), keyType, valueType)
      else -> MapBinder.newMapBinder(binder(), keyType, valueType, annotation.java)
    }
  }

  override fun binder(): Binder = super.binder().skipSources(KAbstractModule::class.java)

  @Suppress("UNCHECKED_CAST") // The type system isn't aware of constructed types.
  private fun <K, V> mapOfType(keyType: Type, valueType: Type): TypeLiteral<Map<K, V>> =
    Types.mapOf(keyType, valueType).typeLiteral() as TypeLiteral<Map<K, V>>

  private fun <T : Any> TypeLiteral<T>.subtype(): Type = Types.subtypeOf(type)
}
