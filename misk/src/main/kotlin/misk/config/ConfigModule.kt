package misk.config

import misk.inject.KAbstractModule

class ConfigModule<T : Config>(
  private val configClass: Class<T>,
  private val appName: String,
  private val config: T
) : KAbstractModule() {
  @Suppress("UNCHECKED_CAST")
  override fun configure() {
    install(AppNameModule(appName))
    bind(configClass).toInstance(config)
    bind<Config>().toInstance(config)
    bind<wisp.config.Config>().toInstance(config)
  }

  companion object {
    inline fun <reified T : Config> create(appName: String, config: T) =
      ConfigModule(T::class.java, appName, config)
  }
}
