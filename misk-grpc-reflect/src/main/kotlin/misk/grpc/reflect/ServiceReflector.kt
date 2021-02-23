package misk.grpc.reflect

import grpc.reflection.v1alpha.ServerReflectionRequest
import grpc.reflection.v1alpha.ServerReflectionResponse

interface ServiceReflector {
  fun process(request: ServerReflectionRequest): ServerReflectionResponse
}
