package misk.inject

import com.google.inject.Binder
import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.TypeLiteral
import com.google.inject.binder.LinkedBindingBuilder
import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.multibindings.Multibinder
import com.google.inject.util.Types
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

inline fun <reified T : Any> LinkedBindingBuilder<in T>.to() = to(T::class.java)

@Suppress("UNCHECKED_CAST")
inline fun <reified T : Any> Binder.newMultibinder(
    annotation: Class<out Annotation>? = null
): Multibinder<T> {
  val typeLiteral =
      parameterizedType<List<*>>(subtypeOf<T>()).typeLiteral() as TypeLiteral<List<T>>
  bind(typeLiteral).toProvider(
      parameterizedType<ListProvider<*>>(
          T::class.java
      ).typeLiteral() as TypeLiteral<Provider<List<T>>>
  )

  return when (annotation) {
    null -> Multibinder.newSetBinder(this, T::class.java)
    else -> Multibinder.newSetBinder(this, T::class.java, annotation)
  }
}

inline fun <reified T : Any, reified A : Annotation> Binder.addMultibinderBindingWithAnnotation() =
    addMultibinderBinding<T>(A::class.java)

@Suppress("UNCHECKED_CAST")
inline fun <reified T : Any> Binder.addMultibinderBinding(
    annotation: Class<out Annotation>? = null
): LinkedBindingBuilder<T> = newMultibinder<T>(annotation).addBinding()

fun ScopedBindingBuilder.asSingleton() {
  `in`(Singleton::class.java)
}

class ListProvider<T> : Provider<List<T>> {
  @Inject
  lateinit var set: MutableSet<T>

  override fun get(): List<T> = ArrayList(set)
}

inline fun <reified T : Any> subtypeOf(): WildcardType {
  return Types.subtypeOf(T::class.java)
}

inline fun <reified T : Any> parameterizedType(vararg typeParameters: Type)
    : ParameterizedType {
  return Types.newParameterizedType(T::class.java, *typeParameters)
}

fun ParameterizedType.typeLiteral() = TypeLiteral.get(this)

fun KType.typeLiteral(): TypeLiteral<*> {
  return TypeLiteral.get(javaType)
}

inline fun <reified T : Any> Injector.getInstance(): T {
  return getInstance(T::class.java)
}

inline fun <reified T : Any> keyOf(): Key<T> = Key.get(T::class.java)
inline fun <reified T : Any> keyOf(a: Annotation): Key<T> = Key.get(T::class.java, a)
inline fun <reified T : Any, A : Annotation> keyOf(a: KClass<A>): Key<T> =
    Key.get(T::class.java, a.java)

fun uninject(target: Any) {
  try {
    var c: Class<*> = target.javaClass
    while (c != Any::class.java) {
      for (f in c.declaredFields) {
        if (f.isAnnotationPresent(Inject::class.java)) {
          f.isAccessible = true
          if (!f.type.isPrimitive) f.set(target, null)
        }
        if (f.isAnnotationPresent(com.google.inject.Inject::class.java)) {
          throw AssertionError("prefer @javax.inject.Inject for " + target.javaClass)
        }
      }
      c = c.superclass
    }
  } catch (e: IllegalAccessException) {
    throw AssertionError(e)
  }

}
