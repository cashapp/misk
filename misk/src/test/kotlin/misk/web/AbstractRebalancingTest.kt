package misk.web

import com.google.inject.Provides
import misk.MiskTestingServiceModule
import misk.client.HttpClientConfig
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientModule
import misk.client.HttpClientSSLConfig
import misk.client.HttpClientsConfig
import misk.inject.KAbstractModule
import misk.security.ssl.SslLoader
import misk.security.ssl.TrustStoreConfig
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import okhttp3.Connection
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Singleton

abstract class AbstractRebalancingTest(
  val percent: Double
) {
  /**
   * To avoid races, we minimize the number of Jetty threads. Jetty's ThreadPoolBudget enforces an
   * unspecified minimum number of threads so we can't use just 1 or 2.
   */
  private val jettyMaxThreadPoolSize = 10

  @MiskTestModule
  val module = TestModule()

  @Inject
  lateinit var jettyService: JettyService

  @Inject
  lateinit var okHttpClient: OkHttpClient

  @Test
  fun http1() {
    test(Protocol.HTTP_1_1)
  }

  @Test @Disabled
  fun http2() {
    test(Protocol.HTTP_2)
  }

  private fun test(protocol: Protocol) {
    val connections = mutableSetOf<Connection>()

    val protocolsList = mutableListOf(protocol)
    if (protocol != Protocol.HTTP_1_1) {
      protocolsList += Protocol.HTTP_1_1 // OkHttp insists on including HTTP/1 in this list.
    }

    val httpClient = okHttpClient.newBuilder()
      .retryOnConnectionFailure(true)
      .protocols(protocolsList)
      .addNetworkInterceptor {
        connections += it.connection()!!
        it.proceed(it.request())
      }
      .build()

    val request = Request.Builder()
      .url(jettyService.httpsServerUrl!!)
      .build()

    val response1 = httpClient.newCall(request).execute()
    response1.use {
      assertThat(it.protocol).isEqualTo(protocol)
    }

    // Make a request for every thread in Jetty's thread pool to defeat races. Otherwise this test
    // will be flaky because a thread could be pre-empted before it sends the close.
    for (i in 1 until jettyMaxThreadPoolSize) {
      httpClient.newCall(request).execute().use {
        assertThat(it.protocol).isEqualTo(protocol)
      }
    }

    checkResponse(response1, connections)
    connections.forEach { it.socket().closeQuietly() }
  }

  abstract fun checkResponse(response: Response, connections: Set<Connection>)

  inner class TestModule : KAbstractModule() {
    override fun configure() {
      install(
        WebServerTestingModule(
          webConfig = WebServerTestingModule.TESTING_WEB_CONFIG.copy(
            close_connection_percent = percent,
            jetty_max_thread_pool_size = jettyMaxThreadPoolSize
          )
        )
      )
      install(MiskTestingServiceModule())
      install(HttpClientModule("default"))
    }

    @Provides
    @Singleton
    fun provideHttpClientsConfig(): HttpClientsConfig {
      return HttpClientsConfig(
        endpoints = mapOf(
          "default" to HttpClientEndpointConfig(
            url = "http://example.com/",
            clientConfig = HttpClientConfig(
              ssl = HttpClientSSLConfig(
                cert_store = null,
                trust_store = TrustStoreConfig(
                  resource = "classpath:/ssl/server_cert.pem",
                  format = SslLoader.FORMAT_PEM
                )
              )
            )
          )
        )
      )
    }
  }
}

@MiskTest(startService = true)
class RebalancingEnabledTest : AbstractRebalancingTest(100.0) {
  override fun checkResponse(response: Response, connections: Set<Connection>) {
    // Only HTTP/1 can carry the 'Connection: close' header.
    if (response.protocol == Protocol.HTTP_1_1) {
      assertThat(response.header("Connection")).isEqualTo("close")
    }
    assertThat(connections).hasSizeGreaterThanOrEqualTo(2)
  }
}

@MiskTest(startService = true)
class RebalancingDisabledTest : AbstractRebalancingTest(0.0) {
  override fun checkResponse(response: Response, connections: Set<Connection>) {
    assertThat(response.header("Connection")).isNull()
    assertThat(connections).hasSize(1)
  }
}
