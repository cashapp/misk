package misk.web.actions

import java.lang.reflect.Method
import java.util.ArrayDeque
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

/** Returns an instance of [T] annotating this method or a method it overrides. */
internal inline fun <reified T : Annotation> KFunction<*>.findAnnotationWithOverrides(): T? {
  return javaMethod!!.findAnnotationWithOverrides(T::class.java)
}

/**
 * Returns an instance of [T] annotating this method or a method it overrides. If multiple
 * overridden methods have the annotation, one is chosen arbitrarily.
 */
internal fun <T : Annotation> Method.findAnnotationWithOverrides(annotationClass: Class<T>): T? {
  for (method in overrides()) {
    val annotation = method.getAnnotation(annotationClass)
    if (annotation != null) {
      return annotation
    }
  }
  return null
}

/** Returns the overrides of this method with overriding methods preceding overridden methods. */
internal fun Method.overrides(): Set<Method> {
  return declaringClass.superclasses()
      .mapNotNull { it.getOverriddenMethod(this@overrides) }
      .toSet()
}

/** Returns the method that [override] overrides. */
internal fun Class<*>.getOverriddenMethod(override: Method): Method? {
  return try {
    check(this.isAssignableFrom(override.declaringClass))
    getDeclaredMethod(override.name, *override.parameterTypes)
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
