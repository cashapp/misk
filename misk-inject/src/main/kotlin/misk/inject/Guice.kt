package misk.inject

import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.Provider
import com.google.inject.TypeLiteral
import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.util.Types
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

fun ScopedBindingBuilder.asSingleton() {
  `in`(Singleton::class.java)
}

/** Given a Set<T>, provide a List<T>. */
internal class ListProvider<T>(
  private val setKey: Key<MutableSet<T>>,
  private val setProvider: Provider<MutableSet<T>>,
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
fun <T> listOfType(elementType: TypeLiteral<T>): TypeLiteral<List<T>> =
  TypeLiteral.get(Types.listOf(elementType.type)) as TypeLiteral<List<T>>

fun <T : Any> listOfType(elementType: KClass<T>) = listOfType(elementType.typeLiteral())

inline fun <reified T : Any> listOfType() = listOfType(T::class)

@Suppress("UNCHECKED_CAST") // The type system isn't aware of constructed types.
fun <T> setOfType(elementType: TypeLiteral<T>): TypeLiteral<Set<T>> =
  TypeLiteral.get(Types.setOf(elementType.type)) as TypeLiteral<Set<T>>

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
fun <T : Any> Injector.getSetOf(type: KClass<T>, annotation: KClass<out Annotation>? = null): Set<T> =
  getInstance(setOfType(type).toKey(annotation))

/**
 * Creates a Guice [Key] for type [T] with an optional annotation qualifier.
 *
 * @param a The annotation instance to use as a qualifier, or null for an unqualified binding
 * @return A [Key] for the specified type and qualifier
 */
inline fun <reified T : Any> keyOf(a: Annotation?): Key<T> = keyOf(a?.qualifier)

/**
 * Creates a Guice [Key] for type [T] with an optional annotation type qualifier.
 *
 * @param a The annotation class to use as a qualifier, or null for an unqualified binding
 * @return A [Key] for the specified type and qualifier
 */
inline fun <reified T : Any> keyOf(a: KClass<out Annotation>?): Key<T> = keyOf(a?.qualifier)

/**
 * Creates a Guice [Key] for type [T] with an optional [BindingQualifier].
 *
 * This is the primary function for creating qualified keys. It handles both type-based and instance-based qualifiers,
 * as well as unqualified bindings.
 *
 * Uses [TypeLiteral] to preserve generic type information when the type is fully specified. Falls back to the erased
 * class when the type contains unresolved type variables (e.g., when called with a non-reified type parameter from an
 * enclosing generic class).
 *
 * @param qualifier The [BindingQualifier] to use, or null for an unqualified binding
 * @return A [Key] for the specified type and qualifier
 */
inline fun <reified T : Any> keyOf(qualifier: BindingQualifier? = null): Key<T> {
  val typeLiteral = object : TypeLiteral<T>() {}
  return if (typeLiteral.type.containsTypeVariable()) {
    when (qualifier) {
      is BindingQualifier.InstanceQualifier -> Key.get(T::class.java, qualifier.annotation)
      is BindingQualifier.TypeClassifier -> Key.get(T::class.java, qualifier.type.java)
      null -> Key.get(T::class.java)
    }
  } else {
    when (qualifier) {
      is BindingQualifier.InstanceQualifier -> Key.get(typeLiteral, qualifier.annotation)
      is BindingQualifier.TypeClassifier -> Key.get(typeLiteral, qualifier.type.java)
      null -> Key.get(typeLiteral)
    }
  }
}

/**
 * Checks whether a [Type] contains any unresolved [TypeVariable]s, recursively inspecting parameterized types, wildcard
 * types, and generic array types.
 */
@PublishedApi
internal fun Type.containsTypeVariable(): Boolean =
  when (this) {
    is TypeVariable<*> -> true
    is ParameterizedType -> actualTypeArguments.any { it.containsTypeVariable() }
    is WildcardType -> upperBounds.any { it.containsTypeVariable() } || lowerBounds.any { it.containsTypeVariable() }
    is GenericArrayType -> genericComponentType.containsTypeVariable()
    else -> false
  }

/**
 * Converts a [TypeLiteral] to a Guice [Key] with an optional annotation type qualifier.
 *
 * @param annotation The annotation class to use as a qualifier, or null for an unqualified binding
 * @return A [Key] for this type literal and the specified qualifier
 */
fun <T : Any> TypeLiteral<T>.toKey(annotation: KClass<out Annotation>?): Key<T> = toKey(annotation?.qualifier)

/**
 * Converts a [TypeLiteral] to a Guice [Key] with an optional annotation instance qualifier.
 *
 * @param annotation The annotation instance to use as a qualifier, or null for an unqualified binding
 * @return A [Key] for this type literal and the specified qualifier
 */
fun <T : Any> TypeLiteral<T>.toKey(annotation: Annotation?): Key<T> = toKey(annotation?.qualifier)

/**
 * Converts a [TypeLiteral] to a Guice [Key] with an optional [BindingQualifier].
 *
 * This is the primary function for converting type literals to qualified keys. It handles both type-based and
 * instance-based qualifiers, as well as unqualified bindings.
 *
 * @param qualifier The [BindingQualifier] to use, or null for an unqualified binding
 * @return A [Key] for this type literal and the specified qualifier
 */
fun <T : Any> TypeLiteral<T>.toKey(qualifier: BindingQualifier? = null): Key<T> {
  return when (qualifier) {
    is BindingQualifier.InstanceQualifier -> Key.get(this, qualifier.annotation)
    is BindingQualifier.TypeClassifier -> Key.get(this, qualifier.type.java)
    null -> Key.get(this)
  }
}

fun <T : Any> KClass<T>.toKey(qualifier: KClass<out Annotation>? = null): Key<T> =
  typeLiteral().toKey(qualifier?.qualifier)

fun <T : Any> KClass<T>.toKey(qualifier: Annotation?): Key<T> = typeLiteral().toKey(qualifier?.qualifier)

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
          throw AssertionError("prefer @jakarta.inject.Inject for " + target.javaClass)
        }
      }
      c = c.superclass
    }
  } catch (e: IllegalAccessException) {
    throw AssertionError(e)
  }
}
