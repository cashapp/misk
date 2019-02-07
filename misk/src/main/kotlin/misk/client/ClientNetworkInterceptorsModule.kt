package misk.client

import misk.inject.KAbstractModule

/**
 * The common set of [ClientNetworkInterceptor]s for all misk apps.
 */
class ClientNetworkInterceptorsModule : KAbstractModule() {
  override fun configure() {
    multibind<ClientNetworkInterceptor.Factory>().to<ClientMetricsInterceptor.Factory>()
  }
}
