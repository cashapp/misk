package misk.grpc.vanilla.wireserver

import com.google.inject.Provides
import io.grpc.BindableService
import misk.ServiceModule
import misk.inject.KAbstractModule
import javax.inject.Named

/** A module that runs a standard gRPC server with Wire protos: generated Wire protos and a Google gRPC Netty backend. */
class RouteGuideWireServiceModule : KAbstractModule() {
  override fun configure() {
    multibind<BindableService>().to<RouteGuideWireServiceImpl>()
    install(ServiceModule<WireGrpcGuavaService>())
  }

  @Provides
  @Named("grpc server")
  fun provideServerUrl(protocGrpcService: WireGrpcGuavaService) = protocGrpcService.url
}
