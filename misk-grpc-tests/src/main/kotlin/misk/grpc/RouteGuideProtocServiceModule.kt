package misk.grpc

import com.google.common.util.concurrent.Service
import io.grpc.BindableService
import misk.inject.KAbstractModule

class RouteGuideProtocServiceModule : KAbstractModule() {
  override fun configure() {
    multibind<BindableService>().to<RouteGuideProtocService>()
    multibind<Service>().to<ProtocGrpcService>()
  }
}
