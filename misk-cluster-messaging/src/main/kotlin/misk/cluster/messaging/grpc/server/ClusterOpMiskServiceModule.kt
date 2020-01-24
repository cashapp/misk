package misk.cluster.messaging.grpc.server

import com.google.inject.Provides
import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.jetty.JettyService
import javax.inject.Named

/** A module that runs a Misk gRPC server: Wire protos and a Jetty backend. */
class ClusterOpMiskServiceModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<ClusterOpGrpcAction>())
  }

  @Provides
  @Named("grpc server")
  fun provideServerUrl(jetty: JettyService) = jetty.httpsServerUrl!!
}
