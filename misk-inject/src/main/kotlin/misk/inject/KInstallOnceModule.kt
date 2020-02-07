package misk.inject

/**
 * It's safe to call [KAbstractModule.install] on multiple instances of this module.
 * Guice will only install it once.
 */
abstract class KInstallOnceModule : KAbstractModule() {
  override fun hashCode(): Int {
    return javaClass.hashCode()
  }

  override fun equals(other: Any?): Boolean {
    return other != null && javaClass == other.javaClass
  }
}