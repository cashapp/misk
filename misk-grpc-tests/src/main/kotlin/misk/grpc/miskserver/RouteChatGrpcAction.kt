package misk.grpc.miskserver

import com.squareup.wire.MessageSink
import com.squareup.wire.MessageSource
import misk.grpc.consumeEachAndClose
import misk.web.actions.WebAction
import misk.web.interceptors.LogRequestResponse
import routeguide.RouteGuideRouteChat
import routeguide.RouteNote
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteChatGrpcAction @Inject constructor() : WebAction, RouteGuideRouteChat {
  var welcomeMessage: String? = null

  @LogRequestResponse(sampling = 1.0, includeBody = true)
  override fun RouteChat(
    request: MessageSource<RouteNote>,
    response: MessageSink<RouteNote>
  ) {
    response.use {
      welcomeMessage?.let {
        response.write(RouteNote(message = it))
      }
      request.consumeEachAndClose { routeNote ->
        response.write(RouteNote(message = "ACK: ${routeNote.message}"))
      }
    }
  }
}
