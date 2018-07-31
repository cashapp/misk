package misk.client

import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Provides
import com.google.inject.name.Names
import io.prometheus.client.Histogram
import misk.MiskServiceModule
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.metrics.Metrics
import misk.metrics.count
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.WebActionEntry
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest(startService = true)
internal class ClientMetricsInterceptorTest {
  data class AppRequest(val desiredStatusCode: Int)
  data class AppResponse(val message: String)

  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var jetty: JettyService
  private lateinit var requestDuration: Histogram
  private lateinit var clientMetrics: Metrics
  private lateinit var clientInjector: Injector

  @BeforeEach
  fun createClient() {
    clientInjector = Guice.createInjector(ClientModule(jetty))
    clientMetrics = clientInjector.getInstance()
    requestDuration =
        clientInjector.getInstance(ClientMetricsInterceptor.Factory::class.java).requestDuration

    val client: Pinger = clientInjector.getInstance(Names.named("pinger"))
    assertThat(client.ping(AppRequest(200)).execute().code()).isEqualTo(200)
    assertThat(client.ping(AppRequest(200)).execute().code()).isEqualTo(200)
    assertThat(client.ping(AppRequest(202)).execute().code()).isEqualTo(202)
    assertThat(client.ping(AppRequest(403)).execute().code()).isEqualTo(403)
    assertThat(client.ping(AppRequest(404)).execute().code()).isEqualTo(404)
    assertThat(client.ping(AppRequest(503)).execute().code()).isEqualTo(503)
  }

  @Test
  fun responseCodes() {
    assertThat(requestDuration.labels("pinger.ping", "all").get().count).isEqualTo(6)
    assertThat(requestDuration.labels("pinger.ping", "202").get().count).isEqualTo(1)
    assertThat(requestDuration.labels("pinger.ping", "4xx").get().count).isEqualTo(2)
    assertThat(requestDuration.labels("pinger.ping", "404").get().count).isEqualTo(1)
    assertThat(requestDuration.labels("pinger.ping", "403").get().count).isEqualTo(1)
    assertThat(requestDuration.labels("pinger.ping", "5xx").get().count).isEqualTo(1)
    assertThat(requestDuration.labels("pinger.ping", "503").get().count).isEqualTo(1)
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      multibind<WebActionEntry>().toInstance(WebActionEntry(PingAction::class))
    }
  }

  interface Pinger {
    @POST("/ping")
    @Headers(
        "Accept: " + MediaTypes.APPLICATION_JSON,
        "Content-type: " + MediaTypes.APPLICATION_JSON)
    fun ping(@Body request: AppRequest): Call<AppResponse>
  }

  class PingAction : WebAction {
    @Post("/ping")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun ping(@RequestBody request: AppRequest): Response<AppResponse> {
      return Response(AppResponse("foo"), statusCode = request.desiredStatusCode)
    }
  }

  class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(MiskServiceModule())
      install(TypedHttpClientModule.create<Pinger>("pinger", Names.named("pinger")))
      multibind<ClientNetworkInterceptor.Factory>().to<ClientMetricsInterceptor.Factory>()
    }

    @Provides
    @Singleton
    fun provideHttpClientConfig(): HttpClientsConfig {
      return HttpClientsConfig(
          endpoints = mapOf("pinger" to HttpClientEndpointConfig(jetty.httpServerUrl.toString())))
    }
  }
}