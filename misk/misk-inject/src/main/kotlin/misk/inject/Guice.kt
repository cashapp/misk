package misk.inject

import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.TypeLiteral
import com.google.inject.binder.ScopedBindingBuilder
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

fun ScopedBindingBuilder.asSingleton() {
  `in`(Singleton::class.java)
}

/** Given a Set<T>, provide a List<T>. */
internal class ListProvider<T>(
  private val setKey: Key<MutableSet<T>>,
  private val setProvider: Provider<MutableSet<T>>
) : Provider<List<T>> {
  override fun get(): List<T> = ArrayList(setProvider.get())

  override fun equals(other: Any?): Boolean {
    return other is ListProvider<*> && other.setKey == setKey
  }

  override fun hashCode(): Int {
    return setKey.hashCode()
  }
}

inline fun <reified T : Any> subtypeOf(): WildcardType {
  return Types.subtypeOf(T::class.java)
}

inline fun <reified T : Any> parameterizedType(vararg typeParameters: Type): ParameterizedType {
  return Types.newParameterizedType(T::class.java, *typeParameters)
}

fun Type.typeLiteral() = TypeLiteral.get(this)!!

fun KType.typeLiteral(): TypeLiteral<*> = TypeLiteral.get(javaType)

fun <T : Any> KClass<T>.typeLiteral(): TypeLiteral<T> = TypeLiteral.get(java)

@Suppress("UNCHECKED_CAST") // The type system isn't aware of constructed types.
fun <T> listOfType(elementType: TypeLiteral<T>): TypeLiteral<List<T>> = TypeLiteral.get(
    Types.listOf(elementType.type)) as TypeLiteral<List<T>>

fun <T : Any> listOfType(elementType: KClass<T>) = listOfType(elementType.typeLiteral())

inline fun <reified T : Any> listOfType() = listOfType(T::class)

@Suppress("UNCHECKED_CAST") // The type system isn't aware of constructed types.
fun <T> setOfType(elementType: TypeLiteral<T>): TypeLiteral<Set<T>> = TypeLiteral.get(
    Types.setOf(elementType.type)) as TypeLiteral<Set<T>>

fun <T : Any> setOfType(elementType: KClass<T>) = setOfType(elementType.typeLiteral())

inline fun <reified T : Any> setOfType() = setOfType(T::class)

inline fun <reified K : Any, reified V : Any> mapOfType() = mapOfType(K::class, V::class)

fun <K : Any, V : Any> mapOfType(keyType: KClass<K>, valueType: KClass<V>) =
    mapOfType(keyType.typeLiteral(), valueType.typeLiteral())

@Suppress("UNCHECKED_CAST") // The type system isn't aware of constructed types.
fun <K, V> mapOfType(keyType: TypeLiteral<K>, valueType: TypeLiteral<V>): TypeLiteral<Map<K, V>> =
  Types.mapOf(keyType.type, valueType.type).typeLiteral() as TypeLiteral<Map<K, V>>

inline fun <reified T : Any> Injector.getInstance(annotation: Annotation? = null): T {
  val key = annotation?.let { Key.get(T::class.java, it) } ?: Key.get(T::class.java)
  return getInstance(key)
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> Injector.getSetOf(
  type: KClass<T>,
  annotation: KClass<out Annotation>? = null
): Set<T> = getInstance(setOfType(type).toKey(annotation))

inline fun <reified T : Any> keyOf(): Key<T> = Key.get(T::class.java)
inline fun <reified T : Any> keyOf(a: Annotation): Key<T> = Key.get(T::class.java, a)
/**
 * If annotation is not null, returns a key for @Annotation T, otherwise a key for T.
 */
inline fun <reified T : Any> keyOf(a: KClass<out Annotation>?): Key<T> =
    if (a == null) Key.get(T::class.java) else Key.get(T::class.java, a.java)

fun <T : Any> TypeLiteral<T>.toKey(annotation: KClass<out Annotation>? = null): Key<T> {
  return when (annotation) {
    null -> Key.get(this)
    else -> Key.get(this, annotation.java)
  }
}

fun <T : Any> KClass<T>.toKey(qualifier: KClass<out Annotation>? = null): Key<T> =
    typeLiteral().toKey(qualifier)

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
