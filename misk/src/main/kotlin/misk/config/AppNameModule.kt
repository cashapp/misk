package misk.config

import misk.inject.KAbstractModule

/**
 * Binds a @AppName String to the provided application name
 */
@Deprecated("Use from misk-config instead")
class AppNameModule(private val appName: String) : KAbstractModule() {
  override fun configure() {
    bind<String>().annotatedWith<AppName>().toInstance(appName)
  }
}
