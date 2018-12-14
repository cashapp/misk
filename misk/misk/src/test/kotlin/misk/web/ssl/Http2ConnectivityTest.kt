package misk.web.ssl

import com.google.inject.Guice
import com.google.inject.Provides
import misk.MiskServiceModule
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientModule
import misk.client.HttpClientSSLConfig
import misk.client.HttpClientsConfig
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.security.ssl.SslLoader
import misk.security.ssl.TrustStoreConfig
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.actions.WebActionEntry
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest(startService = true)
class Http2ConnectivityTest {
  @MiskTestModule
  val module = TestModule()

  @Inject
  private lateinit var jetty: JettyService

  private lateinit var client: OkHttpClient

  @BeforeEach
  fun createClient() {
    val clientInjector = Guice.createInjector(ClientModule(jetty))
    client = clientInjector.getInstance()
  }

  @Test
  @Disabled("while we are on java 8 without conscrypt or java9 without jetty alpn dependency")
  fun happyPath() {
    val call = client.newCall(Request.Builder()
        .url(jetty.httpsServerUrl!!.resolve("/hello")!!)
        .build())
    val response = call.execute()
    response.use {
      assertThat(response.protocol()).isEqualTo(Protocol.HTTP_2)
      assertThat(response.body()!!.string()).isEqualTo("hello")
    }
  }

  @Test
  @Disabled("while we are on java 8 without conscrypt or java9 without jetty alpn dependency")
  fun http1ForClientsThatPreferIt() {
    val http1Client = client.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    val call = http1Client.newCall(Request.Builder()
        .url(jetty.httpsServerUrl!!.resolve("/hello")!!)
        .build())
    val response = call.execute()
    response.use {
      assertThat(response.protocol()).isEqualTo(Protocol.HTTP_1_1)
      assertThat(response.body()!!.string()).isEqualTo("hello")
    }
  }

  @Test
  fun http1ForCleartext() {
    val call = client.newCall(Request.Builder()
        .url(jetty.httpServerUrl.resolve("/hello")!!)
        .build())
    val response = call.execute()
    response.use {
      assertThat(response.protocol()).isEqualTo(Protocol.HTTP_1_1)
      assertThat(response.body()!!.string()).isEqualTo("hello")
    }
  }

  class HelloAction : WebAction {
    @Get("/hello")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun sayHello() = "hello"
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      multibind<WebActionEntry>().toInstance(WebActionEntry<HelloAction>())
    }
  }

  // NB: The server doesn't get a port until after it starts so we create the client module
  // _after_ we start the services
  class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(MiskServiceModule())
      install(HttpClientModule("default"))
    }

    @Provides
    @Singleton
    fun provideHttpClientsConfig(): HttpClientsConfig {
      return HttpClientsConfig(
          endpoints = mapOf(
              "default" to HttpClientEndpointConfig(
                  "http://example.com/",
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
    }
  }
}
