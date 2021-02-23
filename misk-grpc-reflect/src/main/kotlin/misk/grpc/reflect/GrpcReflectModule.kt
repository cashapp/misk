package misk.grpc.reflect

import misk.inject.KAbstractModule
import misk.web.WebActionModule

class GrpcReflectModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<ServerReflectionApi>())
  }
}
