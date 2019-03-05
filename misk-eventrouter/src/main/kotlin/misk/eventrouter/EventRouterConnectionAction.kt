package misk.eventrouter

import misk.web.ConnectWebSocket
import misk.web.actions.WebAction
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EventRouterConnectionAction @Inject constructor() : WebAction {
  @Inject lateinit var realEventRouter: RealEventRouter

  @ConnectWebSocket("/eventrouter")
  fun eventRouter(@Suppress("UNUSED_PARAMETER") webSocket: WebSocket): WebSocketListener {
    return realEventRouter.webSocketListener
  }
}
