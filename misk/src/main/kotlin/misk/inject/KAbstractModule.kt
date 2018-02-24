package misk.inject

import com.google.inject.AbstractModule
import com.google.inject.binder.AnnotatedBindingBuilder
import com.google.inject.binder.LinkedBindingBuilder
import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.multibindings.Multibinder

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

  protected inline fun <reified T : Any> AnnotatedBindingBuilder<in T>.to(): ScopedBindingBuilder {
    return to(T::class.java)
  }

  protected inline fun <reified T : Any> newSetBinder(): Multibinder<T> {
    return Multibinder.newSetBinder(binder(), T::class.java)
  }
}


