package misk.inject

import com.google.inject.Binder
import com.google.inject.TypeLiteral
import com.google.inject.binder.LinkedBindingBuilder
import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.multibindings.Multibinder
import com.google.inject.util.Types
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

internal inline fun <reified T : Any> LinkedBindingBuilder<in T>.to() = to(T::class.java)

@Suppress("UNCHECKED_CAST")
internal inline fun <reified T : Any> Binder.addMultibinderBinding(): LinkedBindingBuilder<T> {
    val typeLiteral = TypeLiteral.get(Types.newParameterizedType(List::class.java, Types.subtypeOf(T::class.java))) as TypeLiteral<List<T>>
    bind(typeLiteral).toProvider(TypeLiteral.get(Types.newParameterizedType(ListProvider::class.java, T::class.java)) as TypeLiteral<Provider<List<T>>>)

    return Multibinder.newSetBinder(this, T::class.java).addBinding()
}

internal fun ScopedBindingBuilder.asSingleton() {
    `in`(Singleton::class.java)
}

internal class ListProvider<T> : Provider<List<T>> {
    @Inject lateinit var set: MutableSet<T>

    override fun get(): List<T> = ArrayList(set)
}

fun uninject(target: Any) {
    // TODO.
}
