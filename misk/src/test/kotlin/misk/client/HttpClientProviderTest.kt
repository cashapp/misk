package misk.client

import com.google.inject.Provides
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest
class HttpClientProviderTest {

  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var mockWebServer: MockWebServer
  @Inject private lateinit var client: OkHttpClient

  @Test
  fun `provides a valid client`() {
    mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("hello"))

    val response = client.newCall(Request.Builder()
        .url(mockWebServer.url("/foo"))
        .build())
        .execute()

    assertThat(response.code).isEqualTo(200)
    response.body?.use { body -> assertThat(body.string()).isEqualTo("hello") }

    assertThat(response.headers["interceptor-header"]).isEqualTo("added by interceptor")
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      bind<MockWebServer>().toInstance(MockWebServer())
      install(HttpClientModule("pinger"))

      // Add an interceptor that adds an HTTP header to the response
      multibind<Interceptor>().toInstance(object : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
          val response = chain.proceed(chain.request())

          return response.newBuilder()
              .addHeader("interceptor-header", "added by interceptor")
              .build()
        }
      })
    }

    @Provides
    @Singleton
    fun provideHttpClientConfig(server: MockWebServer): HttpClientsConfig {
      val url = server.url("/")
      return HttpClientsConfig(
          endpoints = mapOf("pinger" to HttpClientEndpointConfig(
              url = url.toString(),
              clientConfig = HttpClientConfig(
                  readTimeout = Duration.ofMillis(100)
              )
          )))
    }
  }
}
