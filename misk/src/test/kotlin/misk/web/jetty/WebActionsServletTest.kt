package misk.web.jetty

import com.netflix.concurrency.limits.limit.SettableLimit
import com.netflix.concurrency.limits.limiter.SimpleLimiter
import misk.Action
import misk.ApplicationInterceptor
import misk.asAction
import misk.client.UnixDomainSocketFactory
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import misk.time.FakeClockModule
import misk.web.Get
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.ResponseContentType
import misk.web.SocketAddress
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.WebUnixDomainSocketConfig
import misk.web.actions.LivenessCheckAction
import misk.web.actions.ReadinessCheckAction
import misk.web.actions.StatusAction
import misk.web.actions.WebAction
import misk.web.interceptors.UserInterceptorTest
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Duration
import java.util.UUID
import javax.inject.Inject

@MiskTest(startService = true)
class WebActionsServletTest {
  @MiskTestModule
  val module = TestModule()

  @Inject
  internal lateinit var jettyService: JettyService

  private var socketName: String = "@udstest" + UUID.randomUUID().toString()

  @Test
  fun networkSocketSuccess() {
    val response = get("/potato", false)
    assertThat(response.header("ActualSocketName")).isEqualTo(
      with (jettyService.httpServerUrl) { "${host}:${port}" }
    )
  }

  @Test
  fun udsSocketSuccess() {
    val response = get("/potato", true)
    assertThat(response.header("ActualSocketName")).isEqualTo(socketName)
  }

  internal class WebActionsServletNetworkInterceptor : NetworkInterceptor {
    override fun intercept(chain: NetworkChain) {
      chain.httpCall.addResponseHeaders(
        Headers.Builder()
          .set(
            "ActualSocketName",
            with (chain.httpCall.linkLayerAddress) {
              when {
                this is SocketAddress.Network -> "${this.ipAddress}:${this.port}"
                this is SocketAddress.Unix -> this.path
                else -> "null"
              }
            }
          )
          .build())
    }

    class Factory : NetworkInterceptor.Factory {
      override fun create(action: Action): NetworkInterceptor? = WebActionsServletNetworkInterceptor()
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

  private fun get(path: String, viaUDS: Boolean): okhttp3.Response =
    with (Request.Builder().url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())) {
      when {
        viaUDS -> {udsCall(get())}
        else -> {call(get())}
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

  inner class TestModule : KAbstractModule() {
    override fun configure() {
      install(
        WebTestingModule(
          webConfig = WebTestingModule.TESTING_WEB_CONFIG.copy(
            unix_domain_socket = WebUnixDomainSocketConfig(path = socketName)
          )
        )
      )

      multibind<NetworkInterceptor.Factory>().toInstance(
          WebActionsServletTest.WebActionsServletNetworkInterceptor.Factory())

      install(WebActionModule.create<TestAction>())
    }
  }

  companion object {
    internal val TEXT_HEADERS: Headers = Headers.Builder()
        .set("Content-Type", MediaTypes.TEXT_PLAIN_UTF8)
        .build()
  }
}
