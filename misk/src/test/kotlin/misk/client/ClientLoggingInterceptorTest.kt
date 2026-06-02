package misk.client

import com.google.inject.Provides
import com.google.inject.name.Named
import com.google.inject.name.Names
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.logging.LogCollector
import misk.logging.LogCollectorModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.mediatype.MediaTypes
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

@MiskTest(startService = true)
internal class ClientLoggingInterceptorTest {
  @MiskTestModule val module = TestModule()

  @Named("pinger") @Inject private lateinit var client: Pinger
  @Inject private lateinit var mockWebServer: MockWebServer
  @Inject private lateinit var logCollector: LogCollector

  @Test
  fun `logs requests`() {
    mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
    assertThat(client.ping("test", AppRequest(200)).execute().code()).isEqualTo(200)

    assertThat(logCollector.takeMessage(ClientLoggingInterceptor::class))
      .matches("Outgoing request: .*, headers=\\{X-b3-traceid=test.*")
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(LogCollectorModule())
      install(TypedHttpClientModule.create<Pinger>("pinger", Names.named("pinger")))
      bind<MockWebServer>().toInstance(MockWebServer())
    }

    @Provides
    @Singleton
    fun provideHttpClientConfig(server: MockWebServer): HttpClientsConfig {
      val url = server.url("/")
      return HttpClientsConfig(
        endpoints =
          mapOf(
            "pinger" to
              HttpClientEndpointConfig(
                url = url.toString(),
                clientConfig = HttpClientConfig(readTimeout = Duration.ofMillis(100)),
              )
          ),
        logRequests = true,
      )
    }
  }

  data class AppRequest(val desiredStatusCode: Int)

  data class AppResponse(val message: String?)

  interface Pinger {
    @POST("/ping")
    @Headers("Accept: " + MediaTypes.APPLICATION_JSON, "Content-type: " + MediaTypes.APPLICATION_JSON)
    fun ping(@Header("X-b3-traceid") traceId: String, @Body request: AppRequest): Call<AppResponse>
  }
}
