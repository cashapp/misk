package misk.grpc.reflect

import grpc.reflection.v1alpha.ListServiceResponse
import grpc.reflection.v1alpha.ServerReflectionRequest
import grpc.reflection.v1alpha.ServerReflectionResponse
import grpc.reflection.v1alpha.ServiceResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeReflector @Inject constructor() : ServiceReflector {
  override fun process(request: ServerReflectionRequest): ServerReflectionResponse {
    return ServerReflectionResponse.Builder()
      .list_services_response(
        ListServiceResponse.Builder()
          .service(listOf(
            ServiceResponse.Builder()
              .name("BananaService")
              .build())
          ).build()
      ).build()
  }
}
