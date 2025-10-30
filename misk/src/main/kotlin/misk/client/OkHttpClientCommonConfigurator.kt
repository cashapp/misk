package misk.client

import jakarta.inject.Inject
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient.Builder
import java.time.Duration
import java.util.concurrent.TimeUnit

class OkHttpClientCommonConfigurator @Inject constructor() {
  fun configure(builder: Builder, config: HttpClientEndpointConfig): Builder {
    configureCallTimeout(builder = builder, config = config)
    configureConnectTimeout(builder = builder, config = config)
    configureConnectionPool(builder = builder, config = config)
    configureDispatcher(builder = builder, config = config)
    configurePingInterval(builder = builder, config = config)
    configureReadTimeout(builder = builder, config = config)
    configureWriteTimeout(builder = builder, config = config)
    configureRetryOnConnectionFailure(builder = builder, config = config)
    configureFollowRedirects(builder = builder, config = config)
    configureFollowSslRedirects(builder = builder, config = config)
    return builder
  }

  private fun configureCallTimeout(builder: Builder, config: HttpClientEndpointConfig) {
    config.clientConfig.callTimeout?.let { builder.callTimeout(it) }
  }

  private fun configureConnectTimeout(builder: Builder, config: HttpClientEndpointConfig) {
    config.clientConfig.connectTimeout?.let { builder.connectTimeout(it) }
  }

  private fun configureConnectionPool(builder: Builder, config: HttpClientEndpointConfig) {
    val connectionPool = ConnectionPool(
      config.clientConfig.maxIdleConnections ?: maxIdleConnections,
      (config.clientConfig.keepAliveDuration ?: keepAliveDuration).toMillis(),
      TimeUnit.MILLISECONDS
    )
    builder.connectionPool(connectionPool)
  }

  private fun configureDispatcher(builder: Builder, config: HttpClientEndpointConfig) {
    val dispatcher = Dispatcher()
    config.clientConfig.maxRequests?.let { dispatcher.maxRequests = it }
    config.clientConfig.maxRequestsPerHost?.let { dispatcher.maxRequestsPerHost = it }
    builder.dispatcher(dispatcher)
  }

  private fun configurePingInterval(builder: Builder, config: HttpClientEndpointConfig) {
    config.clientConfig.pingInterval?.let { builder.pingInterval(it) }
  }

  private fun configureReadTimeout(builder: Builder, config: HttpClientEndpointConfig) {
    config.clientConfig.readTimeout?.let { builder.readTimeout(it) }
  }

  private fun configureWriteTimeout(builder: Builder, config: HttpClientEndpointConfig) {
    config.clientConfig.writeTimeout?.let { builder.writeTimeout(it) }
  }

  private fun configureRetryOnConnectionFailure(
    builder: Builder,
    config: HttpClientEndpointConfig,
  ) {
    builder.retryOnConnectionFailure(
      config.clientConfig.retryOnConnectionFailure ?: retryOnConnectionFailure
    )
  }

  private fun configureFollowRedirects(
    builder: Builder,
    config: HttpClientEndpointConfig,
  ) {
    builder.followRedirects(
      config.clientConfig.followRedirects ?: followRedirects
    )
  }

  private fun configureFollowSslRedirects(
    builder: Builder,
    config: HttpClientEndpointConfig,
  ) {
    builder.followSslRedirects(
      config.clientConfig.followSslRedirects ?: followSslRedirects
    )
  }

  companion object {
    // Copied from okhttp3.ConnectionPool, as it does not provide "use default" option
    const val maxIdleConnections = 5

    // Copied from okhttp3.ConnectionPool, as it does not provide "use default" option
    val keepAliveDuration: Duration = Duration.ofMinutes(5)

    // For backwards-compat with previous behavior of always setting this to false
    const val retryOnConnectionFailure = false

    // Default value is true
    // https://square.github.io/okhttp/5.x/okhttp/okhttp3/-ok-http-client/-builder/follow-redirects.html
    const val followRedirects = true

    // Default value is true
    // https://square.github.io/okhttp/5.x/okhttp/okhttp3/-ok-http-client/-builder/follow-ssl-redirects.html
    const val followSslRedirects = true
  }
}
