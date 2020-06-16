package misk.client

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.type.TypeFactory
import java.time.Duration

data class BackwardsCompatibleEndpointConfig(
    //Common fields
  val url: String? = null,
  val envoy: HttpClientEnvoyConfig? = null,

    //Legacy fields
  val connectTimeout: Duration? = null,
  val writeTimeout: Duration? = null,
  val readTimeout: Duration? = null,
  val pingInterval: Duration? = null,
  val callTimeout: Duration? = null,
  val maxRequests: Int = 128,
  val maxRequestsPerHost: Int = 32,
  val maxIdleConnections: Int = 100,
  val keepAliveDuration: Duration = Duration.ofMinutes(5),
  val ssl: HttpClientSSLConfig? = null,

    //New fields
  val clientConfig: HttpClientConfig? = null
)

data class BackwardsCompatibleClientsConfig(
    //Legacy fields
  val defaultConnectTimeout: Duration? = null,
  val defaultWriteTimeout: Duration? = null,
  val defaultReadTimeout: Duration? = null,
  val ssl: HttpClientSSLConfig? = null,
  val defaultPingInterval: Duration? = null,
  val defaultCallTimeout: Duration? = null,

    //Shared fields
  val endpoints: Map<String, BackwardsCompatibleEndpointConfig> = mapOf(),

    //New fields
  @JsonAlias("hosts")
  val hostConfigs: LinkedHashMap<String, HttpClientConfig> = linkedMapOf()
)

class BackwardsCompatibleClientsConfigConverter : com.fasterxml.jackson.databind.util.Converter<BackwardsCompatibleClientsConfig, HttpClientsConfig> {
  override fun getInputType(typeFactory: TypeFactory) =
      typeFactory.constructType(BackwardsCompatibleClientsConfig::class.java)

  override fun getOutputType(typeFactory: TypeFactory) =
      typeFactory.constructType(HttpClientsConfig::class.java)

  override fun convert(value: BackwardsCompatibleClientsConfig): HttpClientsConfig {
    //Convert defaults into HttpClientConfig, or null
    val defaults = listOfNotNull(
        value.defaultConnectTimeout,
        value.defaultWriteTimeout,
        value.defaultReadTimeout,
        value.ssl,
        value.defaultPingInterval,
        value.defaultCallTimeout
    )
        .takeUnless { it.isEmpty() }
        ?.let {
          HttpClientConfig(
              connectTimeout = value.defaultConnectTimeout,
              writeTimeout = value.defaultWriteTimeout,
              readTimeout = value.defaultReadTimeout,
              ssl = value.ssl,
              pingInterval = value.defaultPingInterval,
              callTimeout = value.defaultCallTimeout
          )
        }

    //If `defaults` is specified - prepend it to the list of patterns as highest priority
    val hostConfigs = defaults?.let { def ->
      sequence {
        yield(".*" to def)
        yieldAll(value.hostConfigs.asSequence().map { it.toPair() })
      }.toMap(LinkedHashMap())
    } ?: value.hostConfigs

    val endpoints = value.endpoints.mapValues { (_, v) -> convert(v) }

    return HttpClientsConfig(
        hostConfigs = hostConfigs,
        endpoints = endpoints
    )
  }

  private fun convert(value: BackwardsCompatibleEndpointConfig) =
      //http://%s.%s.gns.square
      if (value.clientConfig == null)
        HttpClientEndpointConfig(
            url = value.url,
            envoy = value.envoy,
            clientConfig = HttpClientConfig(
                connectTimeout = value.connectTimeout,
                writeTimeout = value.writeTimeout,
                readTimeout = value.readTimeout,
                pingInterval = value.pingInterval,
                callTimeout = value.callTimeout,
                maxRequests = value.maxRequests,
                maxRequestsPerHost = value.maxRequestsPerHost,
                maxIdleConnections = value.maxIdleConnections,
                keepAliveDuration = value.keepAliveDuration,
                ssl = value.ssl
            )
        )
      else
        HttpClientEndpointConfig(
            url = value.url,
            envoy = value.envoy,
            clientConfig = value.clientConfig
        )
}

@Deprecated("Use default constructor")
fun HttpClientEndpointConfig(
  url: String? = null,
  envoy: HttpClientEnvoyConfig? = null,
  connectTimeout: Duration? = null,
  writeTimeout: Duration? = null,
  readTimeout: Duration? = null,
  pingInterval: Duration? = null,
  callTimeout: Duration? = null,
  maxRequests: Int? = null,
  maxRequestsPerHost: Int? = null,
  maxIdleConnections: Int? = null,
  keepAliveDuration: Duration? = null,
  ssl: HttpClientSSLConfig? = null
) = HttpClientEndpointConfig(
    url = url,
    envoy = envoy,
    clientConfig = HttpClientConfig(
        connectTimeout = connectTimeout,
        writeTimeout = writeTimeout,
        readTimeout = readTimeout,
        pingInterval = pingInterval,
        callTimeout = callTimeout,
        maxRequests = maxRequests,
        maxRequestsPerHost = maxRequestsPerHost,
        maxIdleConnections = maxIdleConnections,
        keepAliveDuration = keepAliveDuration,
        ssl = ssl
    )
)