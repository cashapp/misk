package misk.web.jetty

import jakarta.inject.Inject
import misk.Action
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.ResponseContentType
import misk.web.SocketAddress
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.WebUnixDomainSocketConfig
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import misk.client.UnixDomainSocketFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

@MiskTest(startService = true)
class WebActionsServletTest {
  @MiskTestModule
  val module = TestModule()

  @Inject
  internal lateinit var jettyService: JettyService

  private var socketName: String = "@udstest" + UUID.randomUUID().toString()
  private var fileSocketName: String = "/tmp/udstest" + UUID.randomUUID().toString()

  @Test
  fun networkSocketSuccess() {
    val response = get("/potato", false)
    assertThat(response.header("ActualSocketName")).isEqualTo(
      with(jettyService.httpServerUrl) { "$host:$port" }
    )
  }

  @Test
  fun parseNonAsciiHeaders() {
    val response = get(
      "/potato", false, false,
      Headers.Builder()
        .addUnsafeNonAscii("X-device-name", "Walé Iphone")
        .build()
    )

    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun malformedUriQueryParamsResponseDoesNotContainStacktrace() {
    val response = get(
      path = "/potato",
      viaUDS = false,
      viaFileUDS = false,
      encodedQuery = "test" to "%3C%a%3C",
    )

    assertThat(response.body.string()).isEqualTo("400: Unable to parse URI query")
    assertThat(response.code).isEqualTo(400)
  }

  @Test
  fun udsSocketSuccess() {
    val response = get("/potato", true)
    assertThat(response.header("ActualSocketName")).isEqualTo(socketName)
  }

  @Test
  fun fileUdsSocketSuccess() {
    Assumptions.assumeTrue(isJEP380Supported(fileSocketName))
    assertFilePermissions(fileSocketName)
    val response = get("/potato", false, true)
    assertThat(response.header("ActualSocketName")).isEqualTo(fileSocketName)
  }

  @Test
  fun testPatch404() {
    val response = call(
      Request.Builder()
        .url(jettyService.httpServerUrl.newBuilder().encodedPath("/fooasdf/").build())
        .patch("bar".toRequestBody())
    )
    assertThat(response.body?.string()).contains("Nothing found at PATCH", "fooasdf")
  }

  internal class WebActionsServletNetworkInterceptor : NetworkInterceptor {
    override fun intercept(chain: NetworkChain) {
      chain.httpCall.addResponseHeaders(
        Headers.Builder()
          .set(
            "ActualSocketName",
            with(chain.httpCall.linkLayerLocalAddress) {
              when (this) {
                is SocketAddress.Network -> "${this.ipAddress}:${this.port}"
                is SocketAddress.Unix -> this.path
                else -> "null"
              }
            }
          )
          .build()
      )
    }

    class Factory : NetworkInterceptor.Factory {
      override fun create(action: Action): NetworkInterceptor? =
        WebActionsServletNetworkInterceptor()
    }
  }

  internal class TestAction @Inject constructor() : WebAction {
    @Get("/potato")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call(): TestActionResponse {
      return TestActionResponse("foo")
    }
  }

  internal data class TestActionResponse(val text: String)

  private fun get(
    path: String,
    viaUDS: Boolean,
    viaFileUDS: Boolean = false,
    headers: Headers = Headers.headersOf(),
    encodedQuery: Pair<String, String?>? = null,
  ): okhttp3.Response =
    with(
      Request.Builder()
        .headers(headers)
        .url(
          jettyService.httpServerUrl.newBuilder().encodedPath(path)
            .run {
              if (encodedQuery != null) {
                addEncodedQueryParameter(encodedQuery.first, encodedQuery.second)
              } else {
                this
              }
            }
            .build()
        )
    ) {
      when {
        viaUDS -> {
          udsCall(get())
        }

        viaFileUDS -> {
          fileUdsCall(get())
        }

        else -> {
          call(get())
        }
      }
    }

  private fun call(request: Request.Builder): okhttp3.Response {
    return OkHttpClient().newCall(request.build()).execute()
  }

  private fun udsCall(request: Request.Builder): okhttp3.Response {
    return OkHttpClient().newBuilder()
      .socketFactory(UnixDomainSocketFactory(File(socketName)))
      .build()
      .newCall(request.build())
      .execute()
  }

  private fun fileUdsCall(request: Request.Builder): okhttp3.Response {
    return OkHttpClient().newBuilder()
      .socketFactory(UnixDomainSocketFactory(File(fileSocketName)))
      .build()
      .newCall(request.build())
      .execute()
  }

  private fun assertFilePermissions(path: String) {
    val perm = Files.getPosixFilePermissions(Paths.get(path))
    assertThat(PosixFilePermissions.toString(perm)).isEqualTo("rw-rw-rw-")
  }

  inner class TestModule : KAbstractModule() {
    override fun configure() {
      install(
        WebServerTestingModule(
          webConfig = WebServerTestingModule.TESTING_WEB_CONFIG.copy(
            unix_domain_sockets = listOf(
              WebUnixDomainSocketConfig(path = socketName),
              WebUnixDomainSocketConfig(path = fileSocketName)
            )
          )
        )
      )
      install(MiskTestingServiceModule())

      multibind<NetworkInterceptor.Factory>().toInstance(
        WebActionsServletNetworkInterceptor.Factory()
      )

      install(WebActionModule.create<TestAction>())
    }
  }
}
