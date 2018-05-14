package misk.eventrouter

import misk.web.ConnectWebSocket
import misk.web.actions.WebAction
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EventRouterConnectionAction : WebAction {
  @Inject lateinit var realEventRouter: RealEventRouter

  @ConnectWebSocket("/eventrouter")
  fun eventRouter(webSocket: WebSocket): WebSocketListener {
    return realEventRouter.webSocketListener
  }
}
