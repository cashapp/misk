package misk.client

import java.io.File

/**
 * Envoy configuration provider per endpoint that misk clients can customize to their needs.
 */
interface EnvoyClientEndpointProvider {
  /** Host header that will be used to route the request. */
  fun url(httpClientEnvoyConfig: HttpClientEnvoyConfig): String
  /** Unix socket file to be used to communicate with the local Envoy sidecar. */
  fun unixSocket(httpClientEnvoyConfig: HttpClientEnvoyConfig): File
  /** Port to be used to communicate with the local Envoy sidecar if using HTTP. */
  fun port(httpClientEnvoyConfig: HttpClientEnvoyConfig): Int
}
