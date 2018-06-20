package misk.web.jetty

import com.google.common.util.concurrent.Service
import misk.inject.KAbstractModule

class JettyModule : KAbstractModule() {
  override fun configure() {
    multibind<Service>().to<JettyService>()
  }
}
