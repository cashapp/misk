package misk.grpc.miskserver

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
  fun chat(
    @RequestBody request: GrpcReceiveChannel<RouteNote>,
    response: GrpcSendChannel<RouteNote>
  ) {
    response.use {
      request.consumeEach { routeNote ->
        response.send(RouteNote(message = "ACK: ${routeNote.message}"))
      }
    }

    println("response is done")
  }
}