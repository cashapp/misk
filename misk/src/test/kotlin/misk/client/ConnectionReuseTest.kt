package misk.client

import com.google.inject.Provides
import misk.MiskTestingServiceModule
import misk.endpoints.buildClientEndpointConfig
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.http.GET
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@MiskTest
internal class ConnectionReuseTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var helloService: HelloService
  @Inject private lateinit var helloServiceProvider: Provider<HelloService>
  @Inject private lateinit var server: MockWebServer

  @Test
  fun connectionReused() {
    server.enqueue(MockResponse().setBody("\"a\""))
    server.enqueue(MockResponse().setBody("\"b\""))

    assertThat(helloService.hello().execute().body()).isEqualTo("a")
    assertThat(helloService.hello().execute().body()).isEqualTo("b")

    assertThat(server.takeRequest().sequenceNumber).isEqualTo(0)
    assertThat(server.takeRequest().sequenceNumber).isEqualTo(1)
  }

  @Test
  fun connectionReusedAcrossClients() {
    server.enqueue(MockResponse().setBody("\"a\""))
    server.enqueue(MockResponse().setBody("\"b\""))

    val service1 = helloServiceProvider.get()
    assertThat(service1.hello().execute().body()).isEqualTo("a")
    val service2 = helloServiceProvider.get()
    assertThat(service2.hello().execute().body()).isEqualTo("b")

    assertThat(server.takeRequest().sequenceNumber).isEqualTo(0)
    assertThat(server.takeRequest().sequenceNumber).isEqualTo(1)
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(TypedHttpClientModule.create<HelloService>("hello"))
      bind<MockWebServer>().toInstance(MockWebServer())
    }

    @Provides
    @Singleton
    fun provideHttpClientConfig(server: MockWebServer) = HttpClientsConfig(
        "hello" to server.url("/").buildClientEndpointConfig()
    )
  }

  interface HelloService {
    @GET("/hello")
    fun hello(): Call<String>
  }
}
