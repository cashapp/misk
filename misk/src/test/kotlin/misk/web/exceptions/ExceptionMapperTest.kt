package misk.web.exceptions

import com.squareup.moshi.Moshi
import misk.exceptions.ActionException
import misk.exceptions.StatusCode
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.PathParam
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class ExceptionMapperTest {

  @MiskTestModule
  val module = TestModule()

  @Inject
  lateinit var moshi: Moshi

  @Inject
  lateinit var jettyService: JettyService

  private fun serverUrlBuilder(): HttpUrl.Builder {
    return jettyService.httpServerUrl.newBuilder()
  }

  @Test
  fun masksMessageOnServerError() {
    val response = get("/throws/action/SERVICE_UNAVAILABLE")
    assertThat(response.code).isEqualTo(StatusCode.SERVICE_UNAVAILABLE.code)
    assertThat(response.body?.string()).isEqualTo(StatusCode.SERVICE_UNAVAILABLE.name)
  }

  @Test
  fun returnsMessageOnClientErrors() {
    val response = get("/throws/action/FORBIDDEN")
    assertThat(response.code).isEqualTo(StatusCode.FORBIDDEN.code)
    assertThat(response.body?.string()).isEqualTo("you asked for an error")
  }

  @Test
  fun handlesUnmappedErrorsAsInternalServerError() {
    val response = get("/throws/unmapped-error")
    assertThat(response.code).isEqualTo(StatusCode.INTERNAL_SERVER_ERROR.code)
    assertThat(response.body?.string()).isEqualTo("internal server error")
  }

  fun get(path: String): okhttp3.Response {
    val httpClient = OkHttpClient()
    val request = Request.Builder()
      .get()
      .url(serverUrlBuilder().encodedPath(path).build())
      .build()
    return httpClient.newCall(request).execute()
  }

  class ThrowsActionException @Inject constructor() : WebAction {
    @Get("/throws/action/{statusCode}")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun throwsActionException(@PathParam statusCode: StatusCode): String {
      throw ActionException(statusCode, "you asked for an error")
    }
  }

  class ThrowsUnmappedError @Inject constructor() : WebAction {
    @Get("/throws/unmapped-error")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun throwsUnmappedException(): String {
      throw AssertionError("this was bad")
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.create<ThrowsActionException>())
      install(WebActionModule.create<ThrowsUnmappedError>())
    }
  }
}
