package misk.client

import com.google.inject.Inject

/**
 * Calculates the url for an http client config,
 * which can differ depending on if the client
 * is envoy-based or connects directly.
 */
class HttpClientConfigUrlProvider @Inject constructor() {
  @Inject(optional = true) lateinit var envoyClientEndpointProvider: EnvoyClientEndpointProvider

  fun getUrl(endpointConfig: HttpClientEndpointConfig): String {
    when {
      endpointConfig.url != null -> return endpointConfig.url
      endpointConfig.envoy != null -> return envoyClientEndpointProvider.url(endpointConfig.envoy)
      else -> throw IllegalArgumentException(
          "One of url or envoy configuration must be set for clients")
    }
  }
}
