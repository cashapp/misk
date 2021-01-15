package misk.web

/**
 * Describes how an action is processed. This is like the HTTP method but has special cases for web
 * sockets and gRPC. These protocols layer on top of HTTP methods and have different semantics.
 */
enum class DispatchMechanism {
  GET,
  POST,
  PATCH,
  PUT,
  DELETE,
  GRPC,
  WEBSOCKET;

  /**
   * Returns the method used when a call entered the service.
   *
   * This can be misleading: web sockets don't behave like normal GETs (they are upgraded), and GRPC
   * calls don't behave like normal POSTS (they are duplex).
   */
  val method: String
    get() {
      return when (this) {
        GET -> "GET"
        POST -> "POST"
        PATCH -> "PATCH"
        PUT -> "PUT"
        DELETE -> "DELETE"

        // gRPC layers over POST. https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md
        GRPC -> "POST"

        // WebSocket upgrades from GET.
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Protocol_upgrade_mechanism
        WEBSOCKET -> "GET"
      }
    }
}
