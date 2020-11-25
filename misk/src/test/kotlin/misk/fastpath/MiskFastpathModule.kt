package misk.fastpath

import misk.ApplicationInterceptor
import misk.inject.KAbstractModule
import okhttp3.Interceptor

class MiskFastpathModule : KAbstractModule() {
  override fun configure() {
    multibind<Interceptor>().to<FastpathOutboundInterceptor>()
    multibind<ApplicationInterceptor.Factory>().to<FastpathInboundInterceptor.Factory>()
  }
}
