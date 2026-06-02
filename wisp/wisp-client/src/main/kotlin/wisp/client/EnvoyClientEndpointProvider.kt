package wisp.client

import java.io.File

/** Envoy configuration provider per endpoint that wisp clients can customize to their needs. */
@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith =
    ReplaceWith(expression = "EnvoyClientEndpointProvider", imports = ["misk.client.EnvoyClientEndpointProvider"]),
)
interface EnvoyClientEndpointProvider {
  /** Host header that will be used to route the request. */
  fun url(httpClientEnvoyConfig: HttpClientEnvoyConfig): String

  /** Unix socket file to be used to communicate to the local Envoy sidecar. */
  fun unixSocket(httpClientEnvoyConfig: HttpClientEnvoyConfig): File
}
