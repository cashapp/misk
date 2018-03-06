package misk.web.actions

open class WebSocketListener<T> {
  /** Invoked when a text (type {@code 0x1}) message has been received. */
  open fun onMessage(webSocket: WebSocket<T>, content: T) = Unit

  /**
   * Invoked when the remote peer has indicated that no more incoming messages will be
   * transmitted.
   */
  open fun onClosing(webSocket: WebSocket<T>, code: Int, reason: String?) = Unit

  /**
   * Invoked when both peers have indicated that no more messages will be transmitted and the
   * connection has been successfully released. No further calls to this listener will be made.
   */
  open fun onClosed(webSocket: WebSocket<T>, code: Int, reason: String) = Unit

  /**
   * Invoked when a web socket has been closed due to an error reading from or writing to the
   * network. Both outgoing and incoming messages may have been lost. No further calls to this
   * listener will be made.
   */
  open fun onFailure(webSocket: WebSocket<T>, t: Throwable) = Unit
}
