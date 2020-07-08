package misk.embedded

/**
 * When a child of [RoutingClassLoader] is loading a class, [RoutingClassLoader] will be tried
 * first. If the class should be handled by the child, [RoutingClassLoader] delegates down. On the
 * other hand, if the class should not be handled by the child, [RoutingClassLoader] delegates the
 * call to its parent loader.
 */
abstract class RoutingClassLoader(
  name: String,
  parent: ClassLoader
) : ClassLoader(name, parent) {
  abstract fun shouldRouteToChild(className: String): Boolean

  override fun loadClass(className: String, resolve: Boolean): Class<*> {
    // Use the delegation pattern of [ClassLoader] to route class loading. The class loader will
    // first attempt to load a class from its parent, ie a child delegates to [RoutingClassLoader]
    // first. If the parent (the routing loader) throws a [ClassNotFoundException] then it will load
    // the class from the child loader. Otherwise, route upwards to the routing loader's parent.
    return when {
      shouldRouteToChild(className) -> throw ClassNotFoundException()
      else -> super.loadClass(className, resolve)
    }
  }
}
