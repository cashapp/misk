package misk.client

import misk.endpoints.HttpEndpoint
import java.io.File

/**
 * Envoy configuration provider per endpoint that misk clients can customize to their needs.
 */
interface EnvoyClientEndpointProvider {
  /** Host header that will be used to route the request. */
  fun url(httpClientEnvoyConfig: HttpEndpoint.Envoy): String
  /** Unix socket file to be used to communicate to the local Envoy sidecar. */
  fun unixSocket(httpClientEnvoyConfig: HttpEndpoint.Envoy): File
}
