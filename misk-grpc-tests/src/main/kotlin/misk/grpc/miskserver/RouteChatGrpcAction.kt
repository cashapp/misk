package misk.grpc.miskserver

import misk.grpc.GrpcReceiveChannel
import misk.grpc.GrpcSendChannel
import misk.grpc.consumeEachAndClose
import misk.web.Grpc
import misk.web.actions.WebAction
import misk.web.interceptors.LogRequestResponse
import routeguide.RouteNote
import javax.inject.Inject

class RouteChatGrpcAction @Inject constructor() : WebAction {
  @Grpc("/routeguide.RouteGuide/RouteChat")
  @LogRequestResponse(sampling = 1.0, includeBody = true)
  fun chat(
    request: GrpcReceiveChannel<RouteNote>,
    response: GrpcSendChannel<RouteNote>
  ) {
    response.use {
      request.consumeEachAndClose { routeNote ->
        response.send(RouteNote(message = "ACK: ${routeNote.message}"))
      }
    }
  }
}