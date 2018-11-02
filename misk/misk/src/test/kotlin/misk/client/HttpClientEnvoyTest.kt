package misk.client

import com.google.common.util.concurrent.Service
import com.google.inject.Provides
import com.google.inject.name.Named
import com.google.inject.name.Names
import helpers.protos.Dinosaur
import misk.MiskServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okhttp3.mockwebserver.MockResponse
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

  @Inject private lateinit var webServerService: MockWebServerService
  @Inject @Named("dinosaur") private lateinit var client: DinosaurService

  @Test fun useEnvoyClient() {

    val dinoRequest = Dinosaur.Builder().name("dinoRequest").build()

    val server = webServerService.server!!
    server.enqueue(MockResponse())

    val response = client.postDinosaur(dinoRequest).execute()
    assertThat(response.code()).isEqualTo(200)

    val recordedRequest = server.takeRequest()
    assertThat(recordedRequest.path).isEqualTo("/cooldinos")
    assertThat(recordedRequest.getHeader("Host"))
        .isEqualTo(String.format("%s:%s", server.hostName, server.port))
  }

  interface DinosaurService{
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
      install(MiskServiceModule())

      bind<MockWebServerService>().toInstance(MockWebServerService("@socket"))
      multibind<Service>().to<MockWebServerService>()

      bind<EnvoyClientEndpointProvider>().to<TestEnvoyClientEndpointProvider>()

      install(TypedHttpClientModule.create<DinosaurService>("dinosaur", Names.named("dinosaur")))
    }

    @Provides @Singleton fun provideHttpClientConfig(): HttpClientsConfig {
      return HttpClientsConfig(
          endpoints = mapOf(
              "dinosaur" to HttpClientEndpointConfig(envoy = HttpClientEnvoyConfig("dinosaur"))
          ))
    }
  }
}
