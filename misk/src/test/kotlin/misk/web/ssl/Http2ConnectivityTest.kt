package misk.web.ssl

import ch.qos.logback.classic.Level
import com.google.inject.Guice
import com.google.inject.Provides
import misk.Action
import misk.MiskDefault
import misk.MiskTestingServiceModule
import misk.client.HttpClientConfig
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientModule
import misk.client.HttpClientSSLConfig
import misk.client.HttpClientsConfig
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.logging.LogCollectorModule
import misk.scope.ActionScoped
import misk.security.ssl.SslLoader
import misk.security.ssl.TrustStoreConfig
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.Post
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.interceptors.MetricsInterceptor
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import wisp.logging.LogCollector
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
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
  @Inject private lateinit var metricsInterceptorFactory: MetricsInterceptor.Factory


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
    val call = client.newCall(
      Request.Builder()
        .url(jetty.httpsServerUrl!!.resolve("/hello")!!)
        .build()
    )
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

    val call = http1Client.newCall(
      Request.Builder()
        .url(jetty.httpsServerUrl!!.resolve("/hello")!!)
        .build()
    )
    val response = call.execute()
    response.use {
      assertThat(response.protocol).isEqualTo(Protocol.HTTP_1_1)
      assertThat(response.body!!.string()).isEqualTo("hello")
    }
  }

  @Test
  fun http1ForCleartext() {
    val call = client.newCall(
      Request.Builder()
        .url(jetty.httpServerUrl.resolve("/hello")!!)
        .build()
    )
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
    val call = client.newCall(
      Request.Builder()
        .url(jetty.httpsServerUrl!!.resolve("/disconnect/empty")!!)
        .build()
    )
    assertFailsWith<IOException> {
      call.execute()
    }
  }

  /** Confirm we don't page oncall when HTTP calls fail due to connectivity problems. */
  @Test
  @Disabled("Too flaky; sometimes returns a 499 instead")
  fun disconnectWithLargeResponse() {
    client = client.newBuilder()
      .retryOnConnectionFailure(false)
      .build()
    val call = client.newCall(
      Request.Builder()
        .url(jetty.httpsServerUrl!!.resolve("/disconnect/large")!!)
        .build()
    )
    assertFailsWith<IOException> {
      call.execute()
    }

    val code = module.lockInterceptorFactory.queue.take()
    assertThat(code).isEqualTo(500)

    val requestDuration = metricsInterceptorFactory.requestDuration
    assertThat(
      requestDuration.labels(
        "Http2ConnectivityTest.DisconnectWithLargeResponseAction",
        "unknown",
        "500"
      ).get().count.toInt()
    ).isEqualTo(1)
  }

  /** Confirm we don't page oncall and we record a 499 in the metrics. **/
  @Test
  @Disabled("Too flaky")
  fun clientTimeoutWritingTheRequest() {
    val http1Client = client.newBuilder()
      .protocols(listOf(Protocol.HTTP_1_1))
      .writeTimeout(1, TimeUnit.MILLISECONDS)
      .build()

    val requestBody = object : RequestBody() {
      override fun contentType() = "text/plain;charset=utf-8".toMediaType()

      override fun writeTo(sink: BufferedSink) {
        Thread.sleep(1000L)
        for (i in 0 until 1024 * 1024) {
          sink.writeUtf8("impossible: $i\n")
        }
        throw IOException("boom")
      }
    }

    val call = http1Client.newCall(
      Request.Builder()
        .url(jetty.httpsServerUrl!!.resolve("/large/request")!!)
        .post(requestBody)
        .build()
    )

    assertFailsWith<SocketTimeoutException> {
      call.execute()
    }

    val code = module.lockInterceptorFactory.queue.take()
    assertThat(code).isEqualTo(499)

    val requestDuration = metricsInterceptorFactory.requestDuration
    assertThat(
      requestDuration.labels(
        "Http2ConnectivityTest.DisconnectWithLargeRequestAction",
        "unknown",
        "499"
      ).get().count.toInt()
    ).isEqualTo(1)
  }
  @Test
  fun clientCancelsWritingTheRequest() {
    val http1Client = client.newBuilder()
      .build()

    val requestBody = object : RequestBody() {
      override fun contentType() = "text/plain;charset=utf-8".toMediaType()

      override fun writeTo(sink: BufferedSink) {
        for (i in 0 until 1024 * 1024) {
          sink.writeUtf8("impossible: $i\n")
        }
        throw IOException("boom")
      }
    }

    val call = http1Client.newCall(
      Request.Builder()
        .url(jetty.httpsServerUrl!!.resolve("/large/request")!!)
        .post(requestBody)
        .build()
    )

    assertFailsWith<IOException> {
      call.execute()
    }

    val code = module.lockInterceptorFactory.queue.take()
    assertThat(code).isEqualTo(499)

    val requestDuration = metricsInterceptorFactory.requestDuration
    assertThat(
      requestDuration.labels(
        "Http2ConnectivityTest.DisconnectWithLargeRequestAction",
        "unknown",
        "499"
      ).get().count.toInt()
    ).isEqualTo(1)
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

      return object : ResponseBody {
        override fun writeTo(sink: BufferedSink) {
          for (i in 0 until 1024 * 1024) {
            sink.writeUtf8("impossible\n")
          }
        }
      }
    }
  }

  /** Large requests fail later. */
  class DisconnectWithLargeRequestAction @Inject constructor() : WebAction {
    @Post("/large/request")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun disconnect(@misk.web.RequestBody body: String): Response<String> {
      return Response(body = "bye")
    }
  }

  class TestModule : KAbstractModule() {
    val lockInterceptorFactory = LockInterceptor.Factory()
    override fun configure() {
      multibind<NetworkInterceptor.Factory>(MiskDefault::class)
        .toInstance(lockInterceptorFactory)

      install(LogCollectorModule())
      install(
        WebServerTestingModule(
          webConfig = WebServerTestingModule.TESTING_WEB_CONFIG
        )
      )
      install(MiskTestingServiceModule())
      install(WebActionModule.create<HelloAction>())
      install(WebActionModule.create<DisconnectWithEmptyResponseAction>())
      install(WebActionModule.create<DisconnectWithLargeResponseAction>())
      install(WebActionModule.create<DisconnectWithLargeRequestAction>())
    }
  }

  class LockInterceptor constructor(val queue: ArrayBlockingQueue<Int>) : NetworkInterceptor {
    override fun intercept(chain: NetworkChain) {
      val statusCode =  try {
        chain.proceed(chain.httpCall)
        chain.httpCall.statusCode
      } catch (e: Exception) {
        -1
      }
      queue.add(statusCode)
    }

    @Singleton
    class Factory @Inject constructor() : NetworkInterceptor.Factory {
      val queue = ArrayBlockingQueue<Int>(1)

      override fun create(action: Action): NetworkInterceptor? {
        return LockInterceptor(queue)
      }

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
