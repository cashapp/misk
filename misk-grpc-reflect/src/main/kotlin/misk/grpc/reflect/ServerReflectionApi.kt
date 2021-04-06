package misk.grpc.reflect

import com.squareup.wire.MessageSink
import com.squareup.wire.MessageSource
import grpc.reflection.v1alpha.ServerReflectionRequest
import grpc.reflection.v1alpha.ServerReflectionResponse
import grpc.reflection.v1alpha.ServerReflectionServerReflectionInfoBlockingServer
import javax.inject.Inject
import misk.web.actions.WebAction

// https://raw.githubusercontent.com/grpc/grpc/master/src/proto/grpc/reflection/v1alpha/reflection.proto
class ServerReflectionApi @Inject constructor(
  private val reflector: ServiceReflector // TODO wire.SchemaReflector - use schema with schemaLoader
) : ServerReflectionServerReflectionInfoBlockingServer, WebAction {
  override fun ServerReflectionInfo(
    requests: MessageSource<ServerReflectionRequest>,
    responses: MessageSink<ServerReflectionResponse>
  ) {
    requests.use {
      responses.use {
        while (true) {
          val serverReflectionRequest = requests.read() ?: break
          responses.write(reflector.process(serverReflectionRequest))
        }
      }
    }
  }
}
