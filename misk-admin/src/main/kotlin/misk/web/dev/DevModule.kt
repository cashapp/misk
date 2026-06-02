package misk.web.dev

import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.web.WebActionModule

class DevModule : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<ReloadSignalService>())
    install(WebActionModule.createWithPrefix<DevCheckReloadAction>(url_path_prefix = "/_dev/"))
  }
}
