package misk.testing

import org.junit.jupiter.api.extension.ExtensionContext

/** Stores an object scoped to the test class on the context */
fun <T> ExtensionContext.store(
    name: String,
    value: T
) {
  val namespace = ExtensionContext.Namespace.create(requiredTestClass)
  getStore(namespace).put(name, value)
}

/** @return A previously stored object scoped to the test class */
inline fun <reified T> ExtensionContext.retrieve(name: String): T {
  val namespace = ExtensionContext.Namespace.create(requiredTestClass)
  return getStore(namespace)[name, T::class.java]
}

