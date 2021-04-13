package misk.web.uds

import com.google.inject.Guice
import com.google.inject.Provides
import misk.MiskTestingServiceModule
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientModule
import misk.client.HttpClientsConfig
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.WebUnixDomainSocketConfig
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wisp.client.UnixDomainSocketFactory
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest(startService = true)
class UDSHttp2ConnectivityTest {
  @MiskTestModule
  val module = TestModule()

  @Inject
  private lateinit var jetty: JettyService

  private lateinit var client: OkHttpClient

  private var socketName: String = "@udstest" + UUID.randomUUID().toString()

  @BeforeEach
  fun createClient() {
    val clientInjector = Guice.createInjector(ClientModule(jetty))
    client = clientInjector.getInstance<OkHttpClient>().newBuilder()
      .socketFactory(UnixDomainSocketFactory(File(socketName)))
      .protocols(listOf(Protocol.HTTP_1_1))
      .build()
  }

  @Test
  fun happyPath() {
    val call = client.newCall(
      Request.Builder()
        .url("http://publicobject.com/hello")
        .build()
    )
    val response = call.execute()
    response.use {
      assertThat(response.protocol).isEqualTo(Protocol.HTTP_1_1)
      assertThat(response.body!!.string()).isEqualTo("hello")
    }
  }

  class HelloAction @Inject constructor() : WebAction {
    @Get("/hello")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun sayHello() = "hello"
  }

  inner class TestModule : KAbstractModule() {
    override fun configure() {
      install(
        WebServerTestingModule(
          webConfig = WebServerTestingModule.TESTING_WEB_CONFIG.copy(
            http2 = true,
            unix_domain_socket = WebUnixDomainSocketConfig(
              path = socketName
            )
          )
        )
      )
      install(MiskTestingServiceModule())
      install(WebActionModule.create<HelloAction>())
    }
  }

  class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(HttpClientModule("default"))
    }

    @Provides
    @Singleton
    fun provideHttpClientsConfig(): HttpClientsConfig {
      return HttpClientsConfig(
        endpoints = mapOf(
          "default" to HttpClientEndpointConfig("http://example.com/")
        )
      )
    }
  }
}
