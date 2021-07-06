package misk.policy.opa

import misk.ServiceModule
import misk.inject.KAbstractModule

class OpaDevelopmentModule : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<LocalOpaService>())
  }
}
