package misk.client

import com.google.inject.Provides
import com.google.inject.name.Named
import com.google.inject.name.Names
import helpers.protos.Dinosaur
import misk.MiskTestingServiceModule
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okhttp3.Protocol
import okhttp3.mockwebserver.MockResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.File
import java.util.Arrays
import javax.inject.Inject
import javax.inject.Singleton

/** Demonstrates ability to bind up an envoy-based http client and connect via unix sockets. */
@MiskTest(startService = true)
internal class HttpClientEnvoyTest {
  @MiskTestModule val module = TestModule()

  @Inject private lateinit var webServerService: MockWebServerService
  @Inject @Named("dinosaur") private lateinit var client: DinosaurService

  // TODO(nb): Fix MockWebServer to support this.
  //
  // The test as-is fails with the following exception:
  // jnr.unixsocket.UnixSocketAddress cannot be cast to java.net.InetSocketAddress
  // java.lang.ClassCastException: jnr.unixsocket.UnixSocketAddress cannot be cast to java.net.InetSocketAddress
  //   at okhttp3.internal.http2.Http2Connection$Builder.socket(Http2Connection.java:559)
  //   at okhttp3.mockwebserver.MockWebServer$3.processConnection(MockWebServer.java:501)
  @Disabled("MockWebServer + HTTP/2 + Unix Sockets not playing together nicely")
  @Test fun useEnvoyClient() {

    val dinoRequest = Dinosaur.Builder().name("dinoRequest").build()

    val server = webServerService.server!!
    server.run { server.protocols = Arrays.asList(Protocol.H2_PRIOR_KNOWLEDGE) }
    server.enqueue(MockResponse())

    val response = client.postDinosaur(dinoRequest).execute()
    assertThat(response.code()).isEqualTo(200)

    val recordedRequest = server.takeRequest()
    assertThat(recordedRequest.path).isEqualTo("/cooldinos")
    assertThat(recordedRequest.getHeader("Host"))
        .isEqualTo(String.format("%s:%s", server.hostName, server.port))
  }

  interface DinosaurService {
    @POST("/cooldinos") fun postDinosaur(@Body request: Dinosaur): Call<Void>
  }

  class TestEnvoyClientEndpointProvider : EnvoyClientEndpointProvider {
    @Inject private lateinit var webServerService: MockWebServerService

    override fun url(httpClientEnvoyConfig: HttpClientEnvoyConfig): String {
      return webServerService.server!!.url("").toString()
    }

    override fun unixSocket(httpClientEnvoyConfig: HttpClientEnvoyConfig): File {
      return File("@socket")
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())

      bind<MockWebServerService>().toInstance(MockWebServerService("@socket"))
      install(ServiceModule<MockWebServerService>())

      bind<EnvoyClientEndpointProvider>().to<TestEnvoyClientEndpointProvider>()

      install(TypedHttpClientModule.create<DinosaurService>("dinosaur", Names.named("dinosaur")))
    }

    @Provides @Singleton fun provideHttpClientConfig(): HttpClientsConfig {
      return HttpClientsConfig(
          endpoints = mapOf(
              "dinosaur" to HttpClientEndpointConfig(envoy = HttpClientEnvoyConfig("dinosaur"))))
    }
  }
}
