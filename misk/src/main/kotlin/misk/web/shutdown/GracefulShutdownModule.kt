package misk.web.shutdown

import misk.MiskDefault
import misk.ServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.web.NetworkInterceptor
import misk.web.WebConfig
import misk.web.jetty.JettyHealthService.Companion.jettyHealthServiceEnabled
import misk.web.jetty.JettyService

internal class GracefulShutdownModule (private val config: WebConfig): KAbstractModule() {
  @OptIn(ExperimentalMiskApi::class)
  override fun configure() {
    if (!config.jettyHealthServiceEnabled() || config.graceful_shutdown_config?.disabled != false) {
      return
    }

    install(
      ServiceModule<GracefulShutdownService>()
        .dependsOn<JettyService>()
    )

    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
      .to<GracefulShutdownInterceptorFactory>()
  }
}
