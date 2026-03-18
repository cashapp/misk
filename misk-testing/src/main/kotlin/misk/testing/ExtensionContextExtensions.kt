package misk.testing

import org.junit.jupiter.api.extension.ExtensionContext

internal val ExtensionContext.rootRequiredTestInstance: Any
  get() = requiredTestInstances.allInstances.first()

internal val ExtensionContext.rootRequiredTestClass: Class<*>
  get() = rootRequiredTestInstance.javaClass

/** Stores an object scoped to the test class on the class-level context */
fun <T> ExtensionContext.store(name: String, value: T) {
  val classContext = classLevelContext
  val namespace = ExtensionContext.Namespace.create(classContext.requiredTestClass)
  classContext.getStore(namespace).put(name, value)
}

/** @return A previously stored object scoped to the test class */
inline fun <reified T> ExtensionContext.retrieve(name: String): T = retrieve(name, T::class.java)

@PublishedApi
internal fun <T> ExtensionContext.retrieve(name: String, type: Class<T>): T {
  val classContext = classLevelContext
  val namespace = ExtensionContext.Namespace.create(classContext.requiredTestClass)
  return classContext.getStore(namespace).get(name, type)
}

/**
 * Retrieve value of [storeKey] from the [ExtensionContext] store. If no value exists, compute using [creator], save in
 * store and return the new value.
 *
 * Values are stored on the class-level context so they are shared across parameterized test invocations, which are
 * sibling contexts that cannot see each other's store entries.
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
  val classContext = classLevelContext
  val namespace = ExtensionContext.Namespace.create(classContext.requiredTestClass)
  return classContext.getStore(namespace).getOrComputeIfAbsent(storeKey, { this.creator() }, typeClass)
}

/**
 * Walks up the [ExtensionContext] hierarchy to find the class-level context. For parameterized tests, invocation
 * contexts are siblings under a template context, so storing values at the class level ensures they are shared.
 */
internal val ExtensionContext.classLevelContext: ExtensionContext
  get() {
    var ctx = this
    while (ctx.testMethod.isPresent && ctx.parent.isPresent) {
      ctx = ctx.parent.get()
    }
    return ctx
  }

/** Find [A]-annotated [T]s on the outermost test class and recursively on base classes. */
internal inline fun <reified A : Annotation, T> ExtensionContext.fieldsAnnotatedBy(): Iterable<T> =
  fieldsAnnotatedBy(A::class.java)

/** Find [annotation]-annotated [T]s on the outermost test class and recursively on base classes. */
private fun <T> ExtensionContext.fieldsAnnotatedBy(annotation: Class<out Annotation>): Iterable<T> {
  val rootRequiredTestInstance = rootRequiredTestInstance
  @Suppress("UNCHECKED_CAST")
  return generateSequence(rootRequiredTestInstance.javaClass) { c -> c.superclass }
    .flatMap { it.declaredFields.asSequence() }
    .filter { it.isAnnotationPresent(annotation) }
    .map {
      it.isAccessible = true
      it.get(rootRequiredTestInstance) as T
    }
    .toList()
}
