package misk.grpc.miskserver

import misk.inject.KAbstractModule
import misk.web.WebTestingModule
import misk.web.actions.WebActionEntry

/** A module that runs a Misk gRPC server: Wire protos and a Jetty backend. */
class RouteGuideMiskServiceModule : KAbstractModule() {
  override fun configure() {
    install(WebTestingModule())
    multibind<WebActionEntry>().toInstance(WebActionEntry<GetFeatureGrpcAction>())
  }
}
