package misk.web.exceptions

import ch.qos.logback.classic.Level
import com.squareup.moshi.Moshi
import com.squareup.wire.GrpcException
import com.squareup.wire.GrpcStatus
import jakarta.inject.Inject
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_UNAVAILABLE
import misk.MiskTestingServiceModule
import misk.exceptions.UnauthenticatedException
import misk.exceptions.UnauthorizedException
import misk.exceptions.WebActionException
import misk.inject.KAbstractModule
import misk.logging.LogCollector
import misk.logging.LogCollectorModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.PathParam
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class ExceptionMapperTest {

  @MiskTestModule val module = TestModule()

  @Inject lateinit var moshi: Moshi

  @Inject lateinit var jettyService: JettyService

  @Inject lateinit var logCollector: LogCollector

  private fun serverUrlBuilder(): HttpUrl.Builder {
    return jettyService.httpServerUrl.newBuilder()
  }

  @Test
  fun returnsMessageOnServerError() {
    val response = get("/throws/action/503")
    assertThat(response.code).isEqualTo(HTTP_UNAVAILABLE)
    assertThat(response.body?.string()).isEqualTo("you asked for an error")
  }

  @Test
  fun returnsMessageOnClientErrors() {
    val response = get("/throws/action/403")
    assertThat(response.code).isEqualTo(HTTP_FORBIDDEN)
    assertThat(response.body?.string()).isEqualTo("you asked for an error")
  }

  @Test
  fun handlesUnmappedErrorsAsInternalServerError() {
    val response = get("/throws/unmapped-error")
    assertThat(response.code).isEqualTo(HTTP_INTERNAL_ERROR)
    assertThat(response.body?.string()).isEqualTo("internal server error")
  }

  @Test
  fun doesNotPropagateGrpcError() {
    val response = get("/throws/grpc-error")
    assertThat(response.code).isEqualTo(HTTP_INTERNAL_ERROR)
    assertThat(response.body?.string()).isEqualTo("internal server error")
    val loggedError =
      logCollector.takeMessage(loggerClass = ExceptionHandlingInterceptor::class, minLevel = Level.ERROR)
    assertThat(loggedError).isEqualTo("exception dispatching to ExceptionMapperTest.ThrowsGrpcError")
  }

  @Test
  fun returnsMessageOnUnauthenticated() {
    val response = get("/unauthenticated")
    assertThat(response.code).isEqualTo(misk.client.HTTP_UNAUTHORIZED)
    assertThat(response.body?.string()).isEqualTo("unauthenticated")
  }

  @Test
  fun returnsMessageOnUnauthorized() {
    val response = get("/unauthorized")
    assertThat(response.code).isEqualTo(misk.client.HTTP_FORBIDDEN)
    assertThat(response.body?.string()).isEqualTo("unauthorized")
  }

  fun get(path: String): okhttp3.Response {
    val httpClient = OkHttpClient()
    val request = Request.Builder().get().url(serverUrlBuilder().encodedPath(path).build()).build()
    return httpClient.newCall(request).execute()
  }

  class ThrowsActionException @Inject constructor() : WebAction {
    @Get("/throws/action/{statusCode}")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun throwsActionException(@PathParam statusCode: Int): String {
      throw WebActionException(statusCode, "you asked for an error", "log message")
    }
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

  class ThrowsUnmappedError @Inject constructor() : WebAction {
    @Get("/throws/unmapped-error")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun throwsUnmappedException(): String {
      throw AssertionError("this was bad")
    }
  }

  class ThrowsGrpcError @Inject constructor() : WebAction {
    @Get("/throws/grpc-error")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun throwsGrpcError(): String {
      throw GrpcException(grpcStatus = GrpcStatus.UNKNOWN, grpcMessage = "this was bad")
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<ThrowsActionException>())
      install(WebActionModule.create<ThrowsUnmappedError>())
      install(WebActionModule.create<ThrowsGrpcError>())
      install(WebActionModule.create<ThrowsUnauthenticated>())
      install(WebActionModule.create<ThrowsUnauthorized>())
      install(LogCollectorModule())
    }
  }
}
