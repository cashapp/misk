package misk.inject

/**
 * Make it safe to install multiple instances of this module. Guice will only install it once.
 *
 * This eases dependency management of library/core dependencies which benefit from multiple
 * installation sites in varying configurations.
 */
abstract class KInstallOnceModule : KAbstractModule() {

  final override fun hashCode(): Int {
    return javaClass.hashCode()
  }

  final override fun equals(other: Any?): Boolean {
    return other != null && javaClass.equals(other.javaClass)
  }
}
