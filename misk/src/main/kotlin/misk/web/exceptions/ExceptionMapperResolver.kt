package misk.web.exceptions

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.superclasses

@Singleton
class ExceptionMapperResolver @Inject internal constructor(
  private val mappers: @JvmSuppressWildcards Map<KClass<*>, ExceptionMapper<*>>
) {
  private val cache: ConcurrentMap<KClass<*>, ExceptionMapper<Throwable>> = ConcurrentHashMap()

  @Suppress("UNCHECKED_CAST")
  fun mapperFor(th: Throwable): ExceptionMapper<Throwable>? {
    // The resolved Mapper is always the same, so cache it
    return cache.getOrPut(th::class) {
      val mappedException = getSuperclasses(th::class).firstOrNull { mappers.containsKey(it) }
      return mappers[mappedException] as ExceptionMapper<Throwable>?
    }
  }

  private fun getSuperclasses(kClass: KClass<*>): List<KClass<*>> {
    val dfs = DFS()
    dfs.doDFS(kClass)
    return dfs.result
  }

  private class DFS {
    val result: MutableList<KClass<*>> = mutableListOf()

    fun doDFS(node: KClass<*>) {
      result.add(node)
      node.superclasses
        .filter { it.isSubclassOf(Throwable::class) }
        .forEach { doDFS(it) }
    }
  }
}
