package misk.client

import misk.inject.KAbstractModule

class HttpClientsConfigModule(private val config: HttpClientsConfig) : KAbstractModule() {
  override fun configure() {
    bind<HttpClientsConfig>().toInstance(config)
  }
}
