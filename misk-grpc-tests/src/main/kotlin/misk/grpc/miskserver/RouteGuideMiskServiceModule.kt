package misk.grpc.miskserver

import com.google.inject.Provides
import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.jetty.JettyService
import javax.inject.Named

/** A module that runs a Misk gRPC server: Wire protos and a Jetty backend. */
class RouteGuideMiskServiceModule : KAbstractModule() {
  override fun configure() {
    install(WebTestingModule())
    install(WebActionModule.create<GetFeatureGrpcAction>())
  }

  @Provides
  @Named("grpc server")
  fun provideServerUrl(jetty: JettyService) = jetty.httpsServerUrl!!
}
