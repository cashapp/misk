package misk.web

import org.eclipse.jetty.http.HttpMethod

/**
 * Describes how an action is processed. This is like the HTTP method but has special cases for web
 * sockets and gRPC. These protocols layer on top of HTTP methods and have different semantics.
 */
enum class DispatchMechanism {
  GET,
  GRPC,
  POST,
  DELETE,
  WEBSOCKET;

  /**
   * Returns the method used when a call entered the service.
   *
   * This can be misleading: web sockets don't behave like normal GETs (they are upgraded), and GRPC
   * calls don't behave like normal POSTS (they are duplex).
   */
  val method: HttpMethod
    get() {
      return when (this) {
        GET -> HttpMethod.GET

        // gRPC layers over POST. https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md
        GRPC -> HttpMethod.POST

        POST -> HttpMethod.POST
        DELETE -> HttpMethod.DELETE

        // WebSocket upgrades from GET.
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Protocol_upgrade_mechanism
        WEBSOCKET -> HttpMethod.GET
      }
    }
}