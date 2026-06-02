package misk.web.exceptions

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.exceptions.UnauthenticatedException
import misk.exceptions.UnauthorizedException
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class CustomExceptionMapperTest {
  @MiskTestModule val module = TestModule()

  @Inject lateinit var jettyService: JettyService

  @Test
  fun customizeUnauthenticated() {
    val response = get("/unauthenticated")
    assertThat(response.code).isEqualTo(misk.client.HTTP_UNAUTHORIZED)
    assertThat(response.body?.string()).isEqualTo("custom unauthenticated response!")
  }

  @Test
  fun customizeUnauthorized() {
    val response = get("/unauthorized")
    assertThat(response.code).isEqualTo(misk.client.HTTP_FORBIDDEN)
    assertThat(response.body?.string()).isEqualTo("custom unauthorized response!")
  }

  fun get(path: String): okhttp3.Response {
    val httpClient = OkHttpClient()
    val request = Request.Builder().get().url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build()).build()
    return httpClient.newCall(request).execute()
  }

  class ThrowsUnauthenticated @Inject constructor() : WebAction {
    @Get("/unauthenticated")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun throwsUnauthenticated(): String {
      throw UnauthenticatedException()
    }
  }

  class ThrowsUnauthorized @Inject constructor() : WebAction {
    @Get("/unauthorized")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun throwsUnauthorized(): String {
      throw UnauthorizedException()
    }
  }

  class CustomUnauthorizedMapper @Inject constructor() : ExceptionMapper<UnauthorizedException> {
    override fun toResponse(th: UnauthorizedException) =
      Response(statusCode = 403, body = "custom unauthorized response!".toResponseBody())
  }

  class CustomUnauthenticatedMapper @Inject constructor() : ExceptionMapper<UnauthenticatedException> {
    override fun toResponse(th: UnauthenticatedException) =
      Response(statusCode = 401, body = "custom unauthenticated response!".toResponseBody())
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<ThrowsUnauthenticated>())
      install(WebActionModule.create<ThrowsUnauthorized>())
      install(ExceptionMapperModule.create<UnauthenticatedException, CustomUnauthenticatedMapper>())
      install(ExceptionMapperModule.create<UnauthorizedException, CustomUnauthorizedMapper>())
    }
  }
}
