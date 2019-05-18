package misk.grpc.miskserver

import misk.grpc.BlockingGrpcChannel
import misk.grpc.GrpcReceiveChannel
import misk.grpc.GrpcSendChannel
import misk.grpc.consumeEach
import misk.web.Grpc
import misk.web.RequestBody
import misk.web.actions.WebAction
import routeguide.RouteNote
import javax.inject.Inject

// TODO: Misk should pass in the channel rather than returning it.
class RouteChatGrpcAction @Inject constructor() : WebAction {
  @Grpc("/routeguide.RouteGuide/RouteChat")
  fun chat(@RequestBody request: GrpcReceiveChannel<RouteNote>): GrpcSendChannel<RouteNote> {
    val response = BlockingGrpcChannel<RouteNote>()

    Thread {
      response.use { response ->
        request.consumeEach { routeNote ->
          response.send(RouteNote(message = "ACK: ${routeNote.message}"))
        }
      }
    }.start()

    return response
  }
}