package misk.grpc.protocserver

import com.google.common.util.concurrent.Service
import io.grpc.BindableService
import misk.inject.KAbstractModule

/** A module that runs a standard gRPC server: generated protoc protos and a Netty backend. */
class RouteGuideProtocServiceModule : KAbstractModule() {
  override fun configure() {
    multibind<BindableService>().to<RouteGuideProtocService>()
    multibind<Service>().to<ProtocGrpcService>()
  }
}
