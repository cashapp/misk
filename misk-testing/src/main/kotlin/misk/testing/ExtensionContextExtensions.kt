package misk.testing

import org.junit.jupiter.api.extension.ExtensionContext

internal val ExtensionContext.rootRequiredTestInstance: Any
  get() = requiredTestInstances.allInstances.first()

internal val ExtensionContext.rootRequiredTestClass: Class<*>
  get() = rootRequiredTestInstance.javaClass

/** Stores an object scoped to the test class on the context */
fun <T> ExtensionContext.store(name: String, value: T) {
  val namespace = ExtensionContext.Namespace.create(requiredTestClass)
  getStore(namespace).put(name, value)
}

/** @return A previously stored object scoped to the test class */
inline fun <reified T> ExtensionContext.retrieve(name: String): T {
  val namespace = ExtensionContext.Namespace.create(requiredTestClass)
  return getStore(namespace).get(name, T::class.java)
}

/**
 * Retrieve value of [storeKey] from the [ExtensionContext] store. If no value exists, compute using
 * [creator], save in store and return the new value.
 */
internal inline fun <reified T> ExtensionContext.getFromStoreOrCompute(
  storeKey: String,
  noinline creator: ExtensionContext.() -> T,
) = getFromStoreOrCompute(T::class.java, storeKey, creator)

private fun <T> ExtensionContext.getFromStoreOrCompute(
  typeClass: Class<T>,
  storeKey: String,
  creator: ExtensionContext.() -> T,
): T {
  val namespace = ExtensionContext.Namespace.create(rootRequiredTestClass)
  return getStore(namespace).getOrComputeIfAbsent(storeKey, { this.creator() }, typeClass)
}

/** Find [A]-annotated [T]s on the outermost test class and recursively on base classes. */
internal inline fun <reified A : Annotation, T> ExtensionContext.fieldsAnnotatedBy(): Iterable<T> =
  fieldsAnnotatedBy(A::class.java)

/** Find [annotation]-annotated [T]s on the outermost test class and recursively on base classes. */
private fun <T> ExtensionContext.fieldsAnnotatedBy(
  annotation: Class<out Annotation>
): Iterable<T> {
  val rootRequiredTestInstance = rootRequiredTestInstance
  @Suppress("UNCHECKED_CAST")
  return generateSequence(rootRequiredTestInstance.javaClass) { c -> c.superclass }
    .flatMap { it.declaredFields.asSequence() }
    .filter {
      it.isAnnotationPresent(annotation)
    }
    .map {
      it.isAccessible = true
      it.get(rootRequiredTestInstance) as T
    }
    .toList()
}
