package misk.grpc.miskserver

import com.google.inject.Provides
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.metrics.FakeMetricsModule
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.jetty.JettyService
import javax.inject.Named

/** A module that runs a Misk gRPC server: Wire protos and a Jetty backend. */
class RouteGuideMiskServiceModule : KAbstractModule() {
  override fun configure() {
    install(WebServerTestingModule(webConfig = WebServerTestingModule.TESTING_WEB_CONFIG))
    install(Modules.override(MiskTestingServiceModule()).with(FakeMetricsModule()))
    install(WebActionModule.create<GetFeatureGrpcAction>())
    install(WebActionModule.create<RouteChatGrpcAction>())
  }

  @Provides
  @Named("grpc server")
  fun provideServerUrl(jetty: JettyService) = jetty.httpsServerUrl!!
}
