package misk.config

import misk.inject.KAbstractModule
import misk.web.WebActionModule

class ConfigWebModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<ConfigAdminAction>())
  }
}