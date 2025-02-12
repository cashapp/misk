package misk.web.actions

import java.lang.reflect.Method
import java.util.ArrayDeque
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.jvm.javaMethod

/**
 * Returns a function that delegates everything to this. It promotes all annotations from overridden
 * functions to the returned function, and all annotations from overridden parameters to the
 * returned function.
 *
 * Use this to make it easy to get annotations on overridden functions 'for free'.
 */
internal fun <R> KFunction<R>.withOverrides(): KFunction<R> = FunctionWithOverrides(this)

/** Returns the overrides of this method with overriding methods preceding overridden methods. */
internal fun Method.overrides(): Set<Method> {
  return declaringClass.superclasses()
    .mapNotNull { it.getOverriddenMethod(this@overrides) }
    .toSet()
}

/** Returns the method that [override] overrides. */
private fun Class<*>.getOverriddenMethod(override: Method): Method? {
  return try {
    check(this.isAssignableFrom(override.declaringClass))
    val overridden = getDeclaredMethod(override.name, *override.parameterTypes)
    return overridden.preferNonSynthetic()
  } catch (_: NoSuchMethodException) {
    null
  }
}

/**
 * Returns a set containing this class and all of its transitive superclasses. The returned set
 * starts with this and iterates in breadth-first order.
 */
internal fun Class<*>.superclasses(): Set<Class<*>> {
  val queue = ArrayDeque<Class<*>>()
  queue += this

  val result = mutableSetOf<Class<*>>()
  while (true) {
    val type = queue.poll() ?: break
    result += type

    if (type.superclass != null) {
      queue += type.superclass
    }
    queue += type.interfaces
  }
  return result
}

/**
 * Attempts to find a non-synthetic method with the same declaring class, name, and parameters. If
 * this is already non-synthetic it is returned immediately. If no non-synthetic method is found
 * this method is returned.
 *
 * ### Synthetic Methods?
 *
 * Sometimes the compiler generates multiple methods with the same name and parameters, but
 * different return types. This is caused by covariant return types. Consider this class:
 *
 * ```
 * class PointIterator implements Iterator<Point> {
 *   override boolean hasNext() { ... }
 *   override Point next() { ... }
 * }
 * ```
 *
 * When compiled, javac creates a synthetic `next()` method that returns `Object` to conform to the
 * `Iterator` interface:
 *
 * ```
 * public boolean hasNext()
 * public Point next()
 * public synthetic bridge Object next()
 * ```
 *
 * We prefer non-synthetic methods for reflection because that's where annotations will be.
 */
internal fun Method.preferNonSynthetic(): Method {
  if (!isSynthetic) return this

  for (peer in declaringClass.methods) {
    if (!peer.isSynthetic &&
      peer.name == name &&
      peer.parameterTypes.contentEquals(parameterTypes)
    ) {
      return peer
    }
  }

  return this
}

internal val KFunction<*>.javaMethod: Method?
  get() = (this as? FunctionWithOverrides)?.function?.javaMethod ?: this.javaMethod

internal class FunctionWithOverrides<out R>(
  val function: KFunction<R>
) : KFunction<R> by function {
  private val methodOverrides = function.javaMethod!!.overrides()

  override val annotations: List<Annotation> =
    methodOverrides.flatMap { it.annotations.toList() }

  override val parameters: List<KParameter> =
    function.parameters.mapIndexed { index, parameter ->
      ParameterWithOverrides(
        parameter,
        methodOverrides.flatMap { override ->
          when (index) {
            0 -> listOf()
            else -> override.parameters[index - 1].annotations.toList()
          }
        }
      )
    }

  override fun callBy(args: Map<KParameter, Any?>): R {
    val parameters = args.mapKeys { (key, _) ->
      function.parameters[key.index]
    }
    return function.callBy(parameters)
  }

  suspend fun callSuspendBy(args: Map<KParameter, Any?>): R {
    val parameters = args.mapKeys { (key, _) ->
      function.parameters[key.index]
    }
    return function.callSuspendBy(parameters)
  }

  override fun toString(): String {
    return function.toString()
  }
}

private class ParameterWithOverrides(
  val parameter: KParameter,
  override val annotations: List<Annotation>,
) : KParameter by parameter
