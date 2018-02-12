package misk.web.jetty

import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
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
