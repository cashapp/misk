package misk.inject

import com.google.inject.AbstractModule
import com.google.inject.Binder
import com.google.inject.Key
import com.google.inject.Provider
import com.google.inject.TypeLiteral
import com.google.inject.binder.AnnotatedBindingBuilder
import com.google.inject.binder.LinkedBindingBuilder
import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.multibindings.MapBinder
import com.google.inject.multibindings.Multibinder
import com.google.inject.multibindings.OptionalBinder
import com.google.inject.util.Types
import java.lang.reflect.Type
import kotlin.reflect.KClass
import misk.inject.BindingQualifier.InstanceQualifier
import misk.inject.BindingQualifier.TypeClassifier

/**
 * A class that provides helper methods for working with Kotlin and Guice, allowing implementing classes to operate in
 * the Kotlin type system rather than converting to Java.
 *
 * The more Kotlin friendly API allows calls like:
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
  protected class KotlinAnnotatedBindingBuilder<X>(private val annotatedBuilder: AnnotatedBindingBuilder<X>) :
    AnnotatedBindingBuilder<X> by annotatedBuilder {
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

  protected inline fun <reified T : Any, reified A : Annotation> requireBindingWithAnnotation() {
    requireBinding(Key.get(T::class.java, A::class.java))
  }

  protected inline fun <reified T : Any> multibind(annotation: KClass<out Annotation>?): LinkedBindingBuilder<T> =
    newMultibinder<T>(annotation).addBinding()

  protected inline fun <reified T : Any> multibind(qualifier: BindingQualifier? = null): LinkedBindingBuilder<T> =
    newMultibinder<T>(qualifier).addBinding()

  protected inline fun <reified T : Any, reified A : Annotation> multibind(): LinkedBindingBuilder<T> =
    newMultibinder<T>(A::class).addBinding()

  protected inline fun <reified T : Any> newMultibinder(annotation: KClass<out Annotation>?): Multibinder<T> =
    newMultibinder(T::class, annotation)

  protected inline fun <reified T : Any> newMultibinder(qualifier: BindingQualifier? = null): Multibinder<T> =
    newMultibinder(T::class, qualifier)

  protected inline fun <reified T : Any, reified A : Annotation> newMultibinder(): Multibinder<T> =
    newMultibinder(T::class, A::class)

  protected fun <T : Any> newMultibinder(type: KClass<T>, annotation: KClass<out Annotation>?): Multibinder<T> =
    newMultibinder(type.typeLiteral(), annotation?.qualifier)

  protected fun <T : Any> newMultibinder(type: KClass<T>, qualifier: BindingQualifier? = null): Multibinder<T> =
    newMultibinder(type.typeLiteral(), qualifier)

  @Suppress("UNCHECKED_CAST")
  protected fun <T : Any> newMultibinder(type: TypeLiteral<T>, qualifier: BindingQualifier? = null): Multibinder<T> {
    val setOfT = setOfType(type)
    val mutableSetOfTKey = setOfT.toKey(qualifier) as Key<MutableSet<T>>
    // As of Guice 5.1, Set<? out T> is now bound.
    val listOfT = listOfType(type)
    val listOfOutT = listOfType(type.subtype().typeLiteral()) as TypeLiteral<List<T>>
    val listOfOutTKey = listOfOutT.toKey(qualifier)
    val listOfTKey = listOfT.toKey(qualifier)
    bind(listOfOutTKey).toProvider(ListProvider(mutableSetOfTKey, getProvider(mutableSetOfTKey)))
    bind(listOfTKey).to(listOfOutTKey)

    return when (qualifier) {
      is InstanceQualifier -> Multibinder.newSetBinder(binder(), type, qualifier.annotation)
      is TypeClassifier -> Multibinder.newSetBinder(binder(), type, qualifier.type.java)
      null -> Multibinder.newSetBinder(binder(), type)
    }
  }

  protected inline fun <reified K : Any, reified V : Any> newMapBinder(
    annotation: KClass<out Annotation>? = null
  ): MapBinder<K, V> = newMapBinder(K::class, V::class, annotation)

  protected fun <K : Any, V : Any> newMapBinder(
    keyType: KClass<K>,
    valueType: KClass<V>,
    annotation: KClass<out Annotation>?,
  ): MapBinder<K, V> = newMapBinder(keyType.typeLiteral(), valueType.typeLiteral(), annotation?.qualifier)

  protected inline fun <reified K : Any, reified V : Any> newMapBinder(annotation: Annotation?): MapBinder<K, V> =
    newMapBinder(K::class, V::class, annotation)

  protected fun <K : Any, V : Any> newMapBinder(
    keyType: KClass<K>,
    valueType: KClass<V>,
    annotation: Annotation?,
  ): MapBinder<K, V> = newMapBinder(keyType.typeLiteral(), valueType.typeLiteral(), annotation?.qualifier)

  protected fun <K : Any, V : Any> newMapBinder(
    keyType: KClass<K>,
    valueType: KClass<V>,
    qualifier: BindingQualifier? = null,
  ): MapBinder<K, V> = newMapBinder(keyType.typeLiteral(), valueType.typeLiteral(), qualifier)

  protected fun <K : Any, V : Any> newMapBinder(
    keyType: TypeLiteral<K>,
    valueType: TypeLiteral<V>,
    annotation: KClass<out Annotation>?,
  ): MapBinder<K, V> = newMapBinder(keyType, valueType, annotation?.qualifier)

  protected fun <K : Any, V : Any> newMapBinder(
    keyType: TypeLiteral<K>,
    valueType: TypeLiteral<V>,
    annotation: Annotation?,
  ): MapBinder<K, V> = newMapBinder(keyType, valueType, annotation?.qualifier)

  protected fun <K : Any, V : Any> newMapBinder(
    keyType: TypeLiteral<K>,
    valueType: TypeLiteral<V>,
    qualifier: BindingQualifier? = null,
  ): MapBinder<K, V> {
    val mapOfKV = mapOfType(keyType, valueType).toKey(qualifier)
    // As of Guice 5.1, Map<K, ? out V> is now bound.
    val mapOfOutKV = mapOfType<K, V>(keyType.subtype(), valueType.type).toKey(qualifier)
    val mapOfOutKOutV = mapOfType<K, V>(keyType.subtype(), valueType.subtype()).toKey(qualifier)

    bind(mapOfOutKV).to(mapOfKV)
    bind(mapOfOutKOutV).to(mapOfKV)

    return when (qualifier) {
      is InstanceQualifier -> MapBinder.newMapBinder(binder(), keyType, valueType, qualifier.annotation)
      is TypeClassifier -> MapBinder.newMapBinder(binder(), keyType, valueType, qualifier.type.java)
      null -> MapBinder.newMapBinder(binder(), keyType, valueType)
    }
  }

  protected inline fun <reified K, reified V> getMapProvider(): Provider<Map<K, V>> {
    val key = Key.get(object : TypeLiteral<Map<K, V>>() {})
    return getProvider(key)
  }

  override fun binder(): Binder = super.binder().skipSources(KAbstractModule::class.java)

  @Suppress("UNCHECKED_CAST") // The type system isn't aware of constructed types.
  private fun <K, V> mapOfType(keyType: Type, valueType: Type): TypeLiteral<Map<K, V>> =
    Types.mapOf(keyType, valueType).typeLiteral() as TypeLiteral<Map<K, V>>

  private fun <T : Any> TypeLiteral<T>.subtype(): Type = Types.subtypeOf(type)

  protected fun <T : Any> bindOptional(key: Key<T>): OptionalBinder<T> = OptionalBinder.newOptionalBinder(binder(), key)

  protected fun <T : Any> bindOptional(baseSwitchType: KClass<T>): OptionalBinder<T> =
    OptionalBinder.newOptionalBinder(binder(), baseSwitchType.java)

  protected inline fun <reified T : Any> bindOptional(): OptionalBinder<T> = bindOptional(T::class)

  protected fun <T : Any> bindOptionalDefault(key: Key<T>): LinkedBindingBuilder<T> =
    OptionalBinder.newOptionalBinder(binder(), key).setDefault()

  protected fun <T : Any> bindOptionalDefault(baseSwitchType: KClass<T>): LinkedBindingBuilder<T> =
    OptionalBinder.newOptionalBinder(binder(), baseSwitchType.java).setDefault()

  protected inline fun <reified T : Any> bindOptionalDefault(): LinkedBindingBuilder<T> = bindOptionalDefault(T::class)

  protected fun <T : Any> bindOptionalBinding(key: Key<T>): LinkedBindingBuilder<T> =
    OptionalBinder.newOptionalBinder(binder(), key).setBinding()

  protected fun <T : Any> bindOptionalBinding(baseSwitchType: KClass<T>): LinkedBindingBuilder<T> =
    OptionalBinder.newOptionalBinder(binder(), baseSwitchType.java).setBinding()

  protected inline fun <reified T : Any> bindOptionalBinding(): LinkedBindingBuilder<T> = bindOptionalBinding(T::class)
}
