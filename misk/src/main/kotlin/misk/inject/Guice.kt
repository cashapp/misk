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

internal inline fun <reified T : Any> LinkedBindingBuilder<in T>.to() = to(T::class.java)

internal inline fun <reified T : Any, reified A : Annotation> Binder.addMultibinderBindingWithAnnotation() =
        addMultibinderBinding<T>(A::class.java)

@Suppress("UNCHECKED_CAST")
internal inline fun <reified T : Any> Binder.addMultibinderBinding(
        annotation: Class<out Annotation>? = null
): LinkedBindingBuilder<T> {
    val typeLiteral =
            parameterizedType<List<*>>(subtypeOf<T>()).typeLiteral() as TypeLiteral<List<T>>
    bind(typeLiteral).toProvider(parameterizedType<ListProvider<*>>(
            T::class.java).typeLiteral() as TypeLiteral<Provider<List<T>>>)

    return when (annotation) {
        null -> Multibinder.newSetBinder(this, T::class.java).addBinding()
        else -> Multibinder.newSetBinder(this, T::class.java, annotation).addBinding()
    }
}

internal fun ScopedBindingBuilder.asSingleton() {
    `in`(Singleton::class.java)
}

internal class ListProvider<T> : Provider<List<T>> {
    @Inject lateinit var set: MutableSet<T>

    override fun get(): List<T> = ArrayList(set)
}

internal inline fun <reified T : Any> subtypeOf(): WildcardType {
    return Types.subtypeOf(T::class.java)
}

internal inline fun <reified T : Any> parameterizedType(vararg typeParameters: Type)
        : ParameterizedType {
    return Types.newParameterizedType(T::class.java, *typeParameters)
}

internal fun ParameterizedType.typeLiteral() = TypeLiteral.get(this)

internal fun KType.typeLiteral(): TypeLiteral<*> {
    return TypeLiteral.get(javaType)
}

internal inline fun <reified T : Any> Injector.getInstance(): T {
    return getInstance(T::class.java)
}

internal inline fun <reified T : Any> keyOf(): Key<T> = Key.get(T::class.java)
internal inline fun <reified T : Any> keyOf(a: Annotation): Key<T> = Key.get(T::class.java, a)
internal inline fun <reified T : Any, A : Annotation> keyOf(a: KClass<A>): Key<T> =
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
