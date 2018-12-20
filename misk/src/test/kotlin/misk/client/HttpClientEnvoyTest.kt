package misk.client

import com.google.inject.Provides
import com.google.inject.name.Named
import com.google.inject.name.Names
import helpers.protos.Dinosaur
import misk.MiskServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Demonstrates ability to bind up an envoy-based http client and connect via unix sockets. */
@MiskTest(startService = true)
internal class HttpClientEnvoyTest {
  @MiskTestModule val module = TestModule()
  @Inject @Named("dinosaur") private lateinit var client: DinosaurService

  // TODO(nb): Fix MockWebServer to support testing over Unix Socket
  // Below test with unix sockets fails because of:
  // jnr.unixsocket.UnixSocketAddress cannot be cast to java.net.InetSocketAddress
  // java.lang.ClassCastException: jnr.unixsocket.UnixSocketAddress cannot be cast to java.net.InetSocketAddress
  //   at okhttp3.internal.http2.Http2Connection$Builder.socket(Http2Connection.java:559)
  //   at okhttp3.mockwebserver.MockWebServer$3.processConnection(MockWebServer.java:501)

  @Test fun useEnvoyClientOverHttp() {
    val dinoRequest = Dinosaur.Builder().name("dinoRequest").build()

    val server = MockWebServer()
    server.enqueue(MockResponse())
    server.start(8083)

    val response = client.postDinosaur(dinoRequest).execute()
    assertThat(response.code()).isEqualTo(200)

    val recordedRequest = server.takeRequest()
    assertThat(recordedRequest.path).isEqualTo("/cooldinos")
    assertThat(recordedRequest.getHeader("Host"))
        .isEqualTo("staging.curly.gns.square")
  }

  interface DinosaurService {
    @POST("/cooldinos") fun postDinosaur(@Body request: Dinosaur): Call<Void>
  }

  class TestEnvoyClientEndpointProvider : EnvoyClientEndpointProvider {
    private val port = 8083

    override fun url(httpClientEnvoyConfig: HttpClientEnvoyConfig): String {
      return "http://staging.curly.gns.square/"
    }

    override fun unixSocket(httpClientEnvoyConfig: HttpClientEnvoyConfig): File {
      return File("@socket")
    }

    override fun port(httpClientEnvoyConfig: HttpClientEnvoyConfig): Int {
      return port
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskServiceModule())

      bind<EnvoyClientEndpointProvider>().to<TestEnvoyClientEndpointProvider>()

      install(TypedHttpClientModule.create<DinosaurService>("dinosaur", Names.named("dinosaur")))
    }

    @Provides @Singleton fun provideHttpClientConfig(): HttpClientsConfig {
      return HttpClientsConfig(
          endpoints = mapOf(
              "dinosaur" to HttpClientEndpointConfig(envoy = HttpClientEnvoyConfig("dinosaur", false))))
    }
  }
}
