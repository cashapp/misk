package wisp.client

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient.Builder
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Proxy.Type.HTTP
import java.time.Duration
import java.util.concurrent.TimeUnit

class OkHttpClientCommonConfigurator {
    fun configure(builder: Builder, config: HttpClientEndpointConfig): Builder {
        configureCallTimeout(builder = builder, config = config)
        configureConnectTimeout(builder = builder, config = config)
        configureConnectionPool(builder = builder, config = config)
        configureDispatcher(builder = builder, config = config)
        configurePingInterval(builder = builder, config = config)
        configureReadTimeout(builder = builder, config = config)
        configureWriteTimeout(builder = builder, config = config)
        configureRetryOnConnectionFailure(builder = builder, config = config)
        configureProxy(builder = builder, config = config)
        return builder
    }

    private fun configureProxy(builder: Builder, config: HttpClientEndpointConfig) {
      config.clientConfig.proxy?.also { builder.proxy(Proxy(HTTP, InetSocketAddress(it.hostName, it.port))) }
    }

    private fun configureCallTimeout(builder: Builder, config: HttpClientEndpointConfig) {
        config.clientConfig.callTimeout?.also { builder.callTimeout(it) }
    }

    private fun configureConnectTimeout(builder: Builder, config: HttpClientEndpointConfig) {
        config.clientConfig.connectTimeout?.also { builder.connectTimeout(it) }
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
        config.clientConfig.maxRequests?.also { dispatcher.maxRequests = it }
        config.clientConfig.maxRequestsPerHost?.also { dispatcher.maxRequestsPerHost = it }
        builder.dispatcher(dispatcher)
    }

    private fun configurePingInterval(builder: Builder, config: HttpClientEndpointConfig) {
        config.clientConfig.pingInterval?.also { builder.pingInterval(it) }
    }

    private fun configureReadTimeout(builder: Builder, config: HttpClientEndpointConfig) {
        config.clientConfig.readTimeout?.also { builder.readTimeout(it) }
    }

    private fun configureWriteTimeout(builder: Builder, config: HttpClientEndpointConfig) {
        config.clientConfig.writeTimeout?.also { builder.writeTimeout(it) }
    }

    private fun configureRetryOnConnectionFailure(
        builder: Builder,
        config: HttpClientEndpointConfig,
    ) {
        builder.retryOnConnectionFailure(
          config.clientConfig.retryOnConnectionFailure ?: retryOnConnectionFailure
        )
    }

    companion object {
        // Copied from okhttp3.ConnectionPool, as it does not provide "use default" option
        const val maxIdleConnections = 5

        // Copied from okhttp3.ConnectionPool, as it does not provide "use default" option
        val keepAliveDuration: Duration = Duration.ofMinutes(5)

        // For backwards-compat with previous behavior of always setting this to false
        const val retryOnConnectionFailure = false
    }
}
