package misk.web.actions

import misk.ActionDeadline
import misk.ActionDeadlineProvider
import misk.inject.KAbstractModule
import misk.scope.ActionScoped
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.jetty.JettyService
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import javax.inject.Inject

@MiskTest(startService = true)
class DeadlineTest {

  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var clock: Clock

  @Test
  fun `no deadline`() {
    val httpClient = OkHttpClient()
    val request = Request.Builder()
        .get()
        .url(serverUrlBuilder().encodedPath("/hello").build())
        .build()

    val response = httpClient.newCall(request).execute()
    assertThat(response.code).isEqualTo(200)

    val responseContent = response.body!!.source().readString(Charsets.UTF_8)
    assertThat(responseContent).isEqualTo("no deadline")
  }

  @Test
  fun deadline() {
    val httpClient = OkHttpClient()
    val request = Request.Builder()
        .get()
        .url(serverUrlBuilder().encodedPath("/hello").build())
        .addHeader(ActionDeadlineProvider.HTTP_HEADER, "123")
        .build()

    val response = httpClient.newCall(request).execute()
    assertThat(response.code).isEqualTo(200)

    val responseContent = response.body!!.source().readString(Charsets.UTF_8)
    val expectedDeadline = clock.millis() + 123
    assertThat(responseContent).isEqualTo("$expectedDeadline")
  }

  @Test
  fun `invalid deadline`() {
    listOf("-1", "0", "123ms", "oops").forEach { deadline ->
      val httpClient = OkHttpClient()
      val request = Request.Builder()
          .get()
          .url(serverUrlBuilder().encodedPath("/hello").build())
          .addHeader(ActionDeadlineProvider.HTTP_HEADER, deadline)
          .build()

      val response = httpClient.newCall(request).execute()
      assertThat(response.code).isEqualTo(400)

      val responseContent = response.body!!.source().readString(Charsets.UTF_8)
      assertThat(responseContent)
          .contains("Invalid header value for ${ActionDeadlineProvider.HTTP_HEADER}")
    }
  }

  @Inject
  lateinit var jettyService: JettyService

  private fun serverUrlBuilder(): HttpUrl.Builder {
    return jettyService.httpServerUrl.newBuilder()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.create<GetHello>())
    }
  }

  class GetHello @Inject constructor(
    val deadline: ActionScoped<ActionDeadline>
  ) : WebAction {
    @Get("/hello")
    fun hello(): String {
      return "${deadline.get().current()?.toEpochMilli() ?: "no deadline"}"
    }
  }
}
