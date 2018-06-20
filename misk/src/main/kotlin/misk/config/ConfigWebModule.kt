package misk.config

import misk.inject.KAbstractModule
import misk.web.WebActionModule

//    @TODO(jwilson swankjesse) https://github.com/square/misk/issues/272 hacky fix
class ConfigWebModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<ConfigAdminAction>())
  }
}