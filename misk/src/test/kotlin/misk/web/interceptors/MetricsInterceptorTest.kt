package misk.web.interceptors

import misk.MiskServiceModule
import misk.asAction
import misk.metrics.count
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.Request
import misk.web.Response
import misk.web.actions.WebAction
import misk.web.actions.asNetworkChain
import okhttp3.HttpUrl
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jetty.http.HttpMethod
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class MetricsInterceptorTest {
  @MiskTestModule
  val module = MiskServiceModule()

  @Inject internal lateinit var metricsInterceptorFactory: MetricsInterceptor.Factory
  @Inject internal lateinit var testAction: TestAction

  @BeforeEach
  fun sendRequests() {
    assertThat(invoke(200).statusCode).isEqualTo(200)
    assertThat(invoke(200).statusCode).isEqualTo(200)
    assertThat(invoke(202).statusCode).isEqualTo(202)
    assertThat(invoke(404).statusCode).isEqualTo(404)
    assertThat(invoke(403).statusCode).isEqualTo(403)
    assertThat(invoke(403).statusCode).isEqualTo(403)
  }

  @Test
  fun responseCodes() {
    val requestDuration = metricsInterceptorFactory.requestDuration
    assertThat(requestDuration.labels("TestAction", "all").get().count).isEqualTo(6)
    assertThat(requestDuration.labels("TestAction", "2xx").get().count).isEqualTo(3)
    assertThat(requestDuration.labels("TestAction", "200").get().count).isEqualTo(2)
    assertThat(requestDuration.labels("TestAction", "202").get().count).isEqualTo(1)
    assertThat(requestDuration.labels("TestAction", "4xx").get().count).isEqualTo(3)
    assertThat(requestDuration.labels("TestAction", "404").get().count).isEqualTo(1)
    assertThat(requestDuration.labels("TestAction", "403").get().count).isEqualTo(2)
  }

  fun invoke(desiredStatusCode: Int): Response<String> {
    val request = Request(
        HttpUrl.parse("http://foo.bar/")!!,
        HttpMethod.GET,
        body = Buffer()
    )
    val metricsInterceptor = metricsInterceptorFactory.create(TestAction::call.asAction())
    val chain = testAction.asNetworkChain(TestAction::call, request, metricsInterceptor,
        TerminalInterceptor(desiredStatusCode))

    @Suppress("UNCHECKED_CAST")
    return chain.proceed(chain.request) as Response<String>
  }

  internal class TestAction : WebAction {
    @Get("/call/{result}")
    fun call(desiredStatusCode: Int): Response<String> {
      return Response("foo", statusCode = desiredStatusCode)
    }
  }

  internal class TerminalInterceptor(val status: Int) : NetworkInterceptor {
    override fun intercept(chain: NetworkChain): Response<*> = Response("foo", statusCode = status)
  }
}
