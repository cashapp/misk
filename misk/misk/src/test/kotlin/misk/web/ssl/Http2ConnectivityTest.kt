package misk.web.ssl

import ch.qos.logback.classic.Level
import com.google.inject.Guice
import com.google.inject.Provides
import misk.MiskTestingServiceModule
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientModule
import misk.client.HttpClientSSLConfig
import misk.client.HttpClientsConfig
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.logging.LogCollector
import misk.logging.LogCollectorModule
import misk.scope.ActionScoped
import misk.security.ssl.SslLoader
import misk.security.ssl.TrustStoreConfig
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okio.BufferedSink
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import javax.servlet.http.HttpServletRequest
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
class Http2ConnectivityTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var jetty: JettyService
  @Inject private lateinit var logCollector: LogCollector

  private lateinit var client: OkHttpClient

  @BeforeEach
  fun createClient() {
    val clientInjector = Guice.createInjector(ClientModule(jetty))
    client = clientInjector.getInstance()
  }

  @AfterEach
  fun checkNoErrorsLogged() {
    assertThat(logCollector.takeMessages(minLevel = Level.ERROR)).isEmpty()
  }

  @Test
  fun happyPath() {
    val call = client.newCall(Request.Builder()
        .url(jetty.httpsServerUrl!!.resolve("/hello")!!)
        .build())
    val response = call.execute()
    response.use {
      assertThat(response.protocol).isEqualTo(Protocol.HTTP_2)
      assertThat(response.body!!.string()).isEqualTo("hello")
    }
  }

  @Test
  fun http1ForClientsThatPreferIt() {
    val http1Client = client.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    val call = http1Client.newCall(Request.Builder()
        .url(jetty.httpsServerUrl!!.resolve("/hello")!!)
        .build())
    val response = call.execute()
    response.use {
      assertThat(response.protocol).isEqualTo(Protocol.HTTP_1_1)
      assertThat(response.body!!.string()).isEqualTo("hello")
    }
  }

  @Test
  fun http1ForCleartext() {
    val call = client.newCall(Request.Builder()
        .url(jetty.httpServerUrl.resolve("/hello")!!)
        .build())
    val response = call.execute()
    response.use {
      assertThat(response.protocol).isEqualTo(Protocol.HTTP_1_1)
      assertThat(response.body!!.string()).isEqualTo("hello")
    }
  }

  /** Confirm we don't page oncall when HTTP calls fail due to connectivity problems. */
  @Test
  fun disconnectWithEmptyResponse() {
    client = client.newBuilder()
        .retryOnConnectionFailure(false)
        .build()
    val call = client.newCall(Request.Builder()
        .url(jetty.httpsServerUrl!!.resolve("/disconnect/empty")!!)
        .build())
    assertFailsWith<IOException> {
      call.execute()
    }
  }

  /** Confirm we don't page oncall when HTTP calls fail due to connectivity problems. */
  @Test
  fun disconnectWithLargeResponse() {
    client = client.newBuilder()
        .retryOnConnectionFailure(false)
        .build()
    val call = client.newCall(Request.Builder()
        .url(jetty.httpsServerUrl!!.resolve("/disconnect/large")!!)
        .build())
    assertFailsWith<IOException> {
      call.execute()
    }
  }

  class HelloAction @Inject constructor() : WebAction {
    @Get("/hello")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun sayHello() = "hello"
  }

  class DisconnectWithEmptyResponseAction @Inject constructor(
    private val actionScopedServletRequest: ActionScoped<HttpServletRequest>
  ) : WebAction {
    @Get("/disconnect/empty")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun disconnect(): Response<String> {
      val request = actionScopedServletRequest.get() as org.eclipse.jetty.server.Request
      request.httpChannel.abort(Exception("boom")) // Synthesize a connectivity failure.

      return Response(body = "")
    }
  }

  /** Large responses fail later. */
  class DisconnectWithLargeResponseAction @Inject constructor(
    private val actionScopedServletRequest: ActionScoped<HttpServletRequest>
  ) : WebAction {
    @Get("/disconnect/large")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun disconnect(): ResponseBody {
      val request = actionScopedServletRequest.get() as org.eclipse.jetty.server.Request
      request.httpChannel.abort(Exception("boom")) // Synthesize a connectivity failure.

      return object: ResponseBody {
        override fun writeTo(sink: BufferedSink) {
          for (i in 0 until 1024 * 1024) {
            sink.writeUtf8("impossible\n")
          }
        }
      }
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(LogCollectorModule())
      install(WebTestingModule(webConfig = WebTestingModule.TESTING_WEB_CONFIG.copy(
          http2 = true
      )))
      install(WebActionModule.create<HelloAction>())
      install(WebActionModule.create<DisconnectWithEmptyResponseAction>())
      install(WebActionModule.create<DisconnectWithLargeResponseAction>())
    }
  }

  // NB: The server doesn't get a port until after it starts so we create the client module
  // _after_ we start the services
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
