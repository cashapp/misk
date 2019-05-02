package misk.grpc.miskserver

import misk.web.Grpc
import misk.web.RequestBody
import misk.web.actions.WebAction
import routeguide.RouteNote
import javax.inject.Inject

class RouteChatGrpcAction @Inject constructor() : WebAction {
  @Grpc("/routeguide.RouteGuide/RouteChat")
  fun routeChat(@RequestBody note: RouteNote): RouteNote {
    return note.copy(message = note.message?.reversed())
  }
}