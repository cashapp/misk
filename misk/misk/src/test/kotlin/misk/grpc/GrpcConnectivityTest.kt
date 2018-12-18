package misk.grpc

import com.google.inject.Guice
import com.google.inject.Provides
import com.squareup.protos.test.grpc.HelloReply
import com.squareup.protos.test.grpc.HelloRequest
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
import misk.web.Grpc
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.actions.WebActionEntry
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This test gets Misk running as a GRPC server and then acts as a basic GRPC client to send a
 * request. It's intended to be interoperable with with the
 * [GRPC 'hello world' sample](https://github.com/grpc/grpc-java/tree/master/examples).
 *
 * That sample includes a client and a server that connect to each other. You can also connect this
 * test's client to that sample server, or that sample client to this test's server.
 */
@MiskTest(startService = true)
class GrpcConnectivityTest {
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
  fun happyPath() {
    val request = Request.Builder()
        .url(jetty.httpsServerUrl!!.resolve("/helloworld.Greeter/SayHello")!!)
        .addHeader("grpc-trace-bin", "")
        .addHeader("grpc-accept-encoding", "gzip")
        .post(object : RequestBody() {
          override fun contentType(): MediaType? {
            return MediaTypes.APPLICATION_GRPC_MEDIA_TYPE
          }

          override fun writeTo(sink: BufferedSink) {
            val writer = GrpcWriter.get(sink, HelloRequest.ADAPTER)
            writer.writeMessage(HelloRequest("jesse!"))
          }
        })
        .build()

    val call = client.newCall(request)
    val response = call.execute()

    for (i in 0 until response.headers().size()) {
      println("${response.headers().name(i)}: ${response.headers().value(i)}")
    }

    val reader = GrpcReader.get(response.body()!!.source(), HelloReply.ADAPTER,
        response.header("grpc-encoding"))
    while (true) {
      val message = reader.readMessage() ?: break
      println(message)
    }
  }

  class HelloRpcAction : WebAction {
    @Grpc("/helloworld.Greeter/SayHello")
    fun sayHello(@misk.web.RequestBody request: HelloRequest): HelloReply {
      return HelloReply.Builder()
          .message("howdy, ${request.name}")
          .build()
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      multibind<WebActionEntry>().toInstance(WebActionEntry<HelloRpcAction>())
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