package misk.grpc.protocserver

import com.google.common.util.concurrent.Service
import com.google.inject.Provides
import io.grpc.BindableService
import misk.inject.KAbstractModule
import javax.inject.Named

/** A module that runs a standard gRPC server: generated protoc protos and a Netty backend. */
class RouteGuideProtocServiceModule : KAbstractModule() {
  override fun configure() {
    multibind<BindableService>().to<RouteGuideProtocService>()
    multibind<Service>().to<ProtocGrpcService>()
  }

  @Provides
  @Named("grpc server")
  fun provideServerUrl(protocGrpcService: ProtocGrpcService) = protocGrpcService.url
}
