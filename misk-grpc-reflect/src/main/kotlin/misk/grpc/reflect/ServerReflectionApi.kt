package misk.grpc.reflect

import com.squareup.wire.MessageSink
import com.squareup.wire.MessageSource
import com.squareup.wire.reflector.SchemaReflector
import grpc.reflection.v1alpha.ServerReflectionRequest
import grpc.reflection.v1alpha.ServerReflectionResponse
import grpc.reflection.v1alpha.ServerReflectionServerReflectionInfoBlockingServer
import misk.security.authz.Unauthenticated
import misk.web.actions.WebAction
import javax.inject.Inject

// https://raw.githubusercontent.com/grpc/grpc/master/src/proto/grpc/reflection/v1alpha/reflection.proto
class ServerReflectionApi @Inject constructor(
  private val reflector: SchemaReflector,
) : ServerReflectionServerReflectionInfoBlockingServer, WebAction {
  @Unauthenticated
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
