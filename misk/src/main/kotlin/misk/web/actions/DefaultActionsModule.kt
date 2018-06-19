package misk.web.actions

import misk.Action
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.metrics.web.MetricsJsonAction
import misk.web.NetworkInterceptor
import misk.web.WebActionModule
import misk.web.WideOpenDevelopmentInterceptor
import misk.web.WideOpenDevelopmentInterceptorFactory
import javax.inject.Inject

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


    binder().addMultibinderBinding<NetworkInterceptor.Factory>()
        .to(WideOpenDevelopmentInterceptorFactory::class.java)
  }
}