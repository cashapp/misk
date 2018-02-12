package misk.web.jetty

import misk.logging.getLogger
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import javax.inject.Inject

private val logger = getLogger<Socket>()

class Socket(private val wsConnections: WsConnections) : WebSocketAdapter() {
    override fun onWebSocketConnect(session: Session) {
        super.onWebSocketConnect(session)
        logger.debug { "Socket connected: [session=$session]" }
        wsConnections.subscribe(this, "all")
    }

    override fun onWebSocketClose(
            statusCode: Int,
            reason: String?
    ) {
        super.onWebSocketClose(statusCode, reason)
        logger.debug { "Socket closed: [statusCode=$statusCode][reason=$reason]" }
        wsConnections.unsubscribeAll(this)
    }

    override fun onWebSocketError(cause: Throwable?) {
        super.onWebSocketError(cause)
        logger.error { "Socket error: $cause" }
    }
}
