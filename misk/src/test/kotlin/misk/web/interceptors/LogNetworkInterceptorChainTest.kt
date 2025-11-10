package misk.web.interceptors

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.Action
import misk.MiskTestingServiceModule
import misk.exceptions.BadRequestException
import misk.inject.KAbstractModule
import misk.logging.LogCollector
import misk.logging.LogCollectorModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.RealNetworkChain
import misk.web.Response
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class LogNetworkInterceptorChainTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var logCollector: LogCollector
  @Inject private lateinit var jettyService: JettyService

  @LogNetworkInterceptorChain
  internal class AnnotatedAction @Inject constructor() : WebAction {
    @Get("/annotated")
    fun call(): Response<String> {
      return Response("success")
    }
  }

  internal class NotAnnotatedAction @Inject constructor() : WebAction {
    @Get("/not-annotated")
    fun call(): Response<String> {
      return Response("success")
    }
  }

  @Test
  fun `NotAnnotatedAction does not add extra logs`() {
    get("/not-annotated")

    val logs = logCollector.takeEvents(RealNetworkChain::class)

    assertThat(logs).isEmpty()
  }

  @Test
  fun `AnnotatedAction adds entry and exit logs`() {
    get("/annotated", failState = "true")

    val logs = logCollector.takeEvents(RealNetworkChain::class)

    assertThat(logs).isNotEmpty()
    (0 until (logs.size / 2)).forEach { i ->
      val first = logs[i]
      val last = logs[logs.size - 1 - i]

      val interceptorName = first.argumentArray.last().toString()

      assertThat(first.formattedMessage).startsWith("Interceptor about to process: ")
      assertThat(first.formattedMessage).endsWith(interceptorName)
      assertThat(last.formattedMessage).startsWith("Interceptor finished processing: ")
      assertThat(last.formattedMessage).endsWith(interceptorName)
    }
  }

  @Test
  fun `An AnnotatedAction with a failing intercept still logs`() {
    val result = get("/annotated")
    assertThat(result.code).isEqualTo(400)

    val logs = logCollector.takeEvents(RealNetworkChain::class)
    (0 until (logs.size / 2)).forEach { i ->
      val first = logs[i]
      val interceptorName = first.argumentArray.last().toString()
      // the one that fails doesn't have the "finished processing" log
      if (interceptorName.contains("FailableNetworkInterceptor")) {
        assertThat(logs.count { it.formattedMessage.contains(interceptorName) }).isEqualTo(1)
      } else {
        assertThat(logs.count { it.formattedMessage.contains(interceptorName) }).isEqualTo(2)
      }
    }
  }

  private fun get(path: String, failState: String = "true"): okhttp3.Response {
    val httpClient = OkHttpClient()
    val request = okhttp3.Request.Builder()
      .get()
      .header("fail", failState)
      .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
      .build()
    return httpClient.newCall(request).execute()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(LogCollectorModule())
      install(WebActionModule.create<AnnotatedAction>())
      install(WebActionModule.create<NotAnnotatedAction>())
      multibind<NetworkInterceptor.Factory>().to<FailableNetworkInterceptor.Factory>()
    }
  }
}

class FailableNetworkInterceptor() : NetworkInterceptor{
  override fun intercept(chain: NetworkChain) {
    if (chain.httpCall.requestHeaders["fail"] == "true") {
      throw BadRequestException("Bad Request")
    } else {
      chain.proceed(chain.httpCall)
    }
  }

  @Singleton
  class Factory @Inject constructor() : NetworkInterceptor.Factory{
    override fun create(action: Action): NetworkInterceptor {
      return FailableNetworkInterceptor()
    }
  }
}
