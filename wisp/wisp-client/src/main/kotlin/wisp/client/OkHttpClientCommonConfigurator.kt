package wisp.client

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient.Builder
import java.time.Duration
import java.util.concurrent.TimeUnit

class OkHttpClientCommonConfigurator constructor() {
  fun configure(builder: Builder, config: HttpClientEndpointConfig): Builder {
    configureCallTimeout(builder = builder, config = config)
    configureConnectTimeout(builder = builder, config = config)
    configureConnectionPool(builder = builder, config = config)
    configureDispatcher(builder = builder, config = config)
    configurePingInterval(builder = builder, config = config)
    configureReadTimeout(builder = builder, config = config)
    configureWriteTimeout(builder = builder, config = config)
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

  companion object {
    // Copied from okhttp3.ConnectionPool, as it does not provide "use default" option
    val maxIdleConnections = 5
    // Copied from okhttp3.ConnectionPool, as it does not provide "use default" option
    val keepAliveDuration = Duration.ofMinutes(5)
  }
}
