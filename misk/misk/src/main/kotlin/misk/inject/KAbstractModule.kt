package misk.inject

import com.google.inject.AbstractModule
import com.google.inject.Key
import com.google.inject.TypeLiteral
import com.google.inject.binder.AnnotatedBindingBuilder
import com.google.inject.binder.LinkedBindingBuilder
import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.multibindings.Multibinder
import com.google.inject.util.Types
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

  protected inline fun <reified T : Any> multibind(
    annotation: KClass<out Annotation>? = null
  ): LinkedBindingBuilder<T> = newMultibinder<T>(annotation).addBinding()

  protected inline fun <reified T : Any> newMultibinder(
    annotation: KClass<out Annotation>? = null
  ): Multibinder<T> = newMultibinder(T::class, annotation)

  @Suppress("UNCHECKED_CAST")
  protected fun <T : Any> newMultibinder(
    type: KClass<T>,
    annotation: KClass<out Annotation>? = null
  ): Multibinder<T> {
    val setOfT = parameterizedType<Set<*>>(type.java).typeLiteral() as TypeLiteral<Set<T>>
    val mutableSetOfTKey = setOfT.toKey(annotation) as Key<MutableSet<T>>
    val listOfOutT =
        parameterizedType<List<*>>(Types.subtypeOf(type.java)).typeLiteral() as TypeLiteral<List<T>>
    val listOfOutTKey = listOfOutT.toKey(annotation)
    bind(listOfOutTKey).toProvider(ListProvider(mutableSetOfTKey, getProvider(mutableSetOfTKey)))

    return when (annotation) {
      null -> Multibinder.newSetBinder(binder(), type.java)
      else -> Multibinder.newSetBinder(binder(), type.java, annotation.java)
    }
  }
}
