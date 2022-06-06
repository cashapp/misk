package misk.client

import com.google.inject.Provides
import com.google.inject.name.Named
import com.google.inject.name.Names
import io.prometheus.client.Histogram
import io.prometheus.client.Summary
import java.net.SocketTimeoutException
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.channels.Channel
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Invocation
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.net.URL

@MiskTest
internal class ClientMetricsInterceptorTest {
  data class AppRequest(val desiredStatusCode: Int)
  data class AppResponse(val message: String?)

  @MiskTestModule
  val module = TestModule()

  @Named("pinger") @Inject private lateinit var client: Pinger
  @Named("pingerDelay") @Inject private lateinit var clientDelay: PingerDelay
  @Inject private lateinit var factory: ClientMetricsInterceptor.Factory
  @Inject private lateinit var mockWebServer: MockWebServer

  private lateinit var requestDurationSummary: Summary
  private lateinit var requestDurationHistogram: Histogram

  @BeforeEach
  fun before() {
    requestDurationSummary = factory.requestDuration
    requestDurationHistogram = factory.requestDurationHistogram
  }

  @Test
  fun responseCodes() {
    mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
    mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
    mockWebServer.enqueue(MockResponse().setResponseCode(202).setBody("{}"))
    mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("{}"))
    mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
    mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
    assertThat(client.ping(AppRequest(200)).execute().code()).isEqualTo(200)
    assertThat(client.ping(AppRequest(200)).execute().code()).isEqualTo(200)
    assertThat(client.ping(AppRequest(202)).execute().code()).isEqualTo(202)
    assertThat(client.ping(AppRequest(403)).execute().code()).isEqualTo(403)
    assertThat(client.ping(AppRequest(404)).execute().code()).isEqualTo(404)
    assertThat(client.ping(AppRequest(503)).execute().code()).isEqualTo(503)

    SoftAssertions.assertSoftly { softly ->
      softly.assertThat(requestDurationSummary.labels("pinger.ping", "202").get().count.toInt()).isEqualTo(1)
      softly.assertThat(requestDurationSummary.labels("pinger.ping", "404").get().count.toInt()).isEqualTo(1)
      softly.assertThat(requestDurationSummary.labels("pinger.ping", "403").get().count.toInt()).isEqualTo(1)
      softly.assertThat(requestDurationSummary.labels("pinger.ping", "403").get().count.toInt()).isEqualTo(1)
      softly.assertThat(requestDurationSummary.labels("pinger.ping", "503").get().count.toInt()).isEqualTo(1)

      softly.assertThat(requestDurationHistogram.labels("pinger.ping", "202").get().buckets.last().toInt()).isEqualTo(1)
      softly.assertThat(requestDurationHistogram.labels("pinger.ping", "404").get().buckets.last().toInt()).isEqualTo(1)
      softly.assertThat(requestDurationHistogram.labels("pinger.ping", "403").get().buckets.last().toInt()).isEqualTo(1)
      softly.assertThat(requestDurationHistogram.labels("pinger.ping", "403").get().buckets.last().toInt()).isEqualTo(1)
      softly.assertThat(requestDurationHistogram.labels("pinger.ping", "503").get().buckets.last().toInt()).isEqualTo(1)
    }
  }

  @Test
  fun urlAsAction() {
    val interceptor = factory.create("urlTestClient")

    mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
    val okHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()

    val targetUrl = mockWebServer.url("/path/to/test///?some&get=params") // leading/trailing slashes and GET params will be dropped in the interceptor
    val request = Request.Builder().url(targetUrl).tag(URL::class.java, targetUrl.toUrl()).build()

    okHttpClient.newCall(request).execute().use {
      assertThat(requestDurationSummary.labels("urlTestClient.path.to.test", "200").get().count.toInt()).isEqualTo(1)
      assertThat(requestDurationHistogram.labels("urlTestClient.path.to.test", "200").get().buckets.last().toInt()).isEqualTo(1)
    }
  }

  @Test
  fun timeouts() {
    assertThatExceptionOfType(SocketTimeoutException::class.java).isThrownBy {
      client.ping(AppRequest(200)).execute().code()
    }

    SoftAssertions.assertSoftly { softly ->
      softly.assertThat(requestDurationSummary.labels("pinger.ping", "timeout").get().count.toInt()).isEqualTo(1)
      softly.assertThat(requestDurationHistogram.labels("pinger.ping", "timeout").get().buckets.last().toInt()).isEqualTo(1)
    }
  }

  @Test
  fun incomplete() {
    // Used to signal when the callback has had a chance to run.
    val channel = Channel<Unit>()

    val call = clientDelay.ping(AppRequest(200))
    call.enqueue(object: Callback<AppResponse> {
      override fun onResponse(call: Call<AppResponse>, response: Response<AppResponse>) {
        runBlocking {
          channel.send(Unit)
        }
      }
      override fun onFailure(call: Call<AppResponse>, t: Throwable) {
        runBlocking {
          channel.send(Unit)
        }
      }
    })

    // Give the call a chance to start executing in the background.
    Thread.sleep(500)
    call.cancel()

    // Ensure the response fails or completes before the remainder of the test runs.
    runBlocking {
      channel.receive()
    }

    SoftAssertions.assertSoftly { softly ->
      softly.assertThat(requestDurationSummary.labels("pingerDelay.ping", "incomplete-response").get().count.toInt()).isEqualTo(1)
      softly.assertThat(requestDurationHistogram.labels("pingerDelay.ping", "incomplete-response").get().buckets.last().toInt()).isEqualTo(1)
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(TypedHttpClientModule.create<Pinger>("pinger", Names.named("pinger")))
      install(TypedHttpClientModule.create<PingerDelay>("pingerDelay", Names.named("pingerDelay")))
      bind<MockWebServer>().toInstance(MockWebServer())
    }

    @Provides
    @Singleton
    fun provideHttpClientConfig(server: MockWebServer): HttpClientsConfig {
      val url = server.url("/")
      return HttpClientsConfig(
        endpoints = mapOf(
          "pinger" to HttpClientEndpointConfig(
            url = url.toString(),
            clientConfig = HttpClientConfig(
              readTimeout = Duration.ofMillis(100)
            )
          ),
          "pingerDelay" to HttpClientEndpointConfig(
            url = url.toString(),
            clientConfig = HttpClientConfig(
              readTimeout = Duration.ofSeconds(5)
            )
          )
        )
      )
    }
  }

  interface Pinger {
    @POST("/ping")
    @Headers(
      "Accept: " + MediaTypes.APPLICATION_JSON,
      "Content-type: " + MediaTypes.APPLICATION_JSON
    )
    fun ping(@Body request: AppRequest): Call<AppResponse>
  }

  interface PingerDelay {
    @POST("/ping")
    @Headers(
      "Accept: " + MediaTypes.APPLICATION_JSON,
      "Content-type: " + MediaTypes.APPLICATION_JSON
    )
    fun ping(@Body request: AppRequest): Call<AppResponse>
  }
}
