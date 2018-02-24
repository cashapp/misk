package misk.web.jetty

import javax.inject.Inject

class SocketCreator : WebSocketCreator {
  @Inject lateinit var wsConnections: WsConnections

  override fun createWebSocket(
      req: ServletUpgradeRequest,
      resp: ServletUpgradeResponse
  ): Any? {
    return Socket(wsConnections)
  }
}
