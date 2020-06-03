package misk.client

import com.google.inject.Inject
import misk.endpoints.HttpClientEndpointConfig

/**
 * Calculates the url for an http client config,
 * which can differ depending on if the client
 * is envoy-based or connects directly.
 */
class HttpClientConfigUrlProvider @Inject constructor() {
  @Inject(optional = true) lateinit var envoyClientEndpointProvider: EnvoyClientEndpointProvider

  fun getUrl(endpointConfig: HttpClientEndpointConfig): String =
      endpointConfig.endpoint.map(
          whenEnvoy = { envoyClientEndpointProvider.url(it) },
          whenUrl = { it.url }
      )
}