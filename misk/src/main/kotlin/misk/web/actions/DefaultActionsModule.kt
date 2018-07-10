package misk.web.actions

import misk.inject.KAbstractModule
import misk.metrics.web.MetricsJsonAction
import misk.web.NetworkInterceptor
import misk.web.WebActionModule
import misk.web.WideOpenDevelopmentInterceptorFactory

/**
 * Installs default actions for handling errors, providing status and readiness, and handling
 * resources that aren't found
 */
class DefaultActionsModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<MetricsJsonAction>())
    install(WebActionModule.create<InternalErrorAction>())
    install(WebActionModule.create<StatusAction>())
    install(WebActionModule.create<ReadinessCheckAction>())
    install(WebActionModule.create<LivenessCheckAction>())
    install(WebActionModule.create<NotFoundAction>())


    multibind<NetworkInterceptor.Factory>().to<WideOpenDevelopmentInterceptorFactory>()
  }
}