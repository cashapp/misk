package misk.grpc.vanilla.miskserver

import com.google.inject.Provides
import io.grpc.BindableService
import misk.ServiceModule
import misk.concurrent.ExecutorServiceFactory
import misk.grpc.vanilla.protocserver.ProtocGrpcService
import misk.grpc.vanilla.wireserver.RouteGuideWireGrpc
import misk.inject.KAbstractModule
import java.util.concurrent.ExecutorService
import javax.inject.Named

/** A module that runs a Misk gRPC server: Wire protos and a Jetty backend. */
// TODO: This needs to execute Misk's interceptor stack. Much more reflection needed.
class RouteGuideMiskServiceModule : KAbstractModule() {
  override fun configure() {
    multibind<BindableService>().to<RouteGuideWireGrpc.RouteGuideImplBase>()
    install(ServiceModule<ProtocGrpcService>())
  }

  @Provides
  @Named("grpc server")
  fun provideServerUrl(protocGrpcService: ProtocGrpcService) = protocGrpcService.url

  @Provides
  fun provideProtocService(
    getFeature: GetFeatureGrpcAction,
    routeChat: RouteChatGrpcAction,
    @Named("wire-grpc-adapter") executorService: ExecutorService
  ): RouteGuideWireGrpc.RouteGuideImplBase {
    return RouteGuideWireGrpc.RouteGuideImplLegacyAdapter(
      getFeature,
      null,
      null,
      routeChat,
      executorService
    )
  }

  @Provides
  @Named("wire-grpc-adapter")
  fun provideExecutorService(executorServiceFactory: ExecutorServiceFactory): ExecutorService {
    return executorServiceFactory.unbounded("wire-grpc-adapter")
  }
}
