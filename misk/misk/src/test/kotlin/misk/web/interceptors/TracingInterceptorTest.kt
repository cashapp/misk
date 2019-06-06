package misk.web.interceptors

import com.google.inject.Guice
import io.opentracing.tag.Tags
import misk.asAction
import misk.exceptions.ActionException
import misk.exceptions.StatusCode
import misk.inject.KAbstractModule
import misk.testing.ConcurrentMockTracer
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.MockTracingBackendModule
import misk.web.FakeHttpCall
import misk.web.Get
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.RealNetworkChain
import misk.web.Response
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class TracingInterceptorTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var tracingInterceptorFactory: TracingInterceptor.Factory
  @Inject private lateinit var tracingTestAction: TracingTestAction
  @Inject private lateinit var tracer: ConcurrentMockTracer
  @Inject private lateinit var jettyService: JettyService

  @Test
  fun initiatesTrace() {
    val tracingInterceptor = tracingInterceptorFactory.create(TracingTestAction::call.asAction())!!
    val httpCall = FakeHttpCall(url = HttpUrl.get("http://foo.bar"))
    val chain = RealNetworkChain(TracingTestAction::call.asAction(), tracingTestAction,
        httpCall, listOf(tracingInterceptor, TerminalInterceptor(200)))

    chain.proceed(chain.httpCall)

    val span = tracer.take()
    assertThat(span.parentId()).isEqualTo(0)
    assertThat(span.tags()).isEqualTo(mapOf(
        "http.method" to "GET",
        "http.status_code" to 200,
        "http.url" to "http://foo.bar/",
        "span.kind" to "server"))
  }

  @Test
  fun looksForParentContext() {
    val tracingInterceptor = tracingInterceptorFactory.create(TracingTestAction::call.asAction())!!
    val httpCall = FakeHttpCall(
        url = HttpUrl.get("http://foo.bar"),
        requestHeaders = Headers.of("spanid", "1", "traceid", "2")
    )
    val chain = RealNetworkChain(TracingTestAction::call.asAction(), tracingTestAction,
        httpCall, listOf(tracingInterceptor, TerminalInterceptor(200)))

    chain.proceed(chain.httpCall)

    val span = tracer.take()
    assertThat(span.parentId()).isEqualTo(1)
  }

  @Test
  fun failedTrace() {
    get("/failed_trace")

    val span = tracer.take()
    assertThat(span.tags().get(Tags.ERROR.key)).isEqualTo(true)
    assertThat(span.tags().get(Tags.HTTP_STATUS.key)).isEqualTo(400)
  }

  @Test
  fun failedTraceWithException() {
    get("/exception_trace")

    val span = tracer.take()
    assertThat(span.tags().get(Tags.ERROR.key)).isEqualTo(true)
    assertThat(span.tags().get(Tags.HTTP_STATUS.key)).isEqualTo(420)
  }

  @Test
  fun nothingIfNotInstalled() {
    val injector = Guice.createInjector()

    val tracingInterceptorFactory: TracingInterceptor.Factory =
        injector.getInstance(TracingInterceptor.Factory::class.java)

    assertThat(tracingInterceptorFactory.create(TracingTestAction::call.asAction())).isNull()
  }

  private fun get(path: String) {
    val httpClient = OkHttpClient()
    val request = okhttp3.Request.Builder()
        .get()
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
        .build()
    httpClient.newCall(request).execute()
  }

  internal class TracingTestAction @Inject constructor() : WebAction {
    @Get("/trace")
    fun call(): Response<String> {
      return Response("success")
    }
  }

  internal class FailedTracingTestAction @Inject constructor() : WebAction {
    @Get("/failed_trace")
    fun call(): Response<String> {
      return Response("no good", statusCode = StatusCode.BAD_REQUEST.code)
    }
  }

  internal class ExceptionThrowingTracingTestAction @Inject constructor() : WebAction {
    @Get("/exception_trace")
    fun call(): Response<String> {
      throw ActionException(StatusCode.ENHANCE_YOUR_CALM, "Chill, man")
    }
  }

  internal class TerminalInterceptor(private val status: Int) : NetworkInterceptor {
    override fun intercept(chain: NetworkChain) {
      chain.httpCall.statusCode = status
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(MockTracingBackendModule())
      install(WebActionModule.create<TracingTestAction>())
      install(WebActionModule.create<FailedTracingTestAction>())
      install(WebActionModule.create<ExceptionThrowingTracingTestAction>())

      bind<TracingInterceptor.Factory>()
    }
  }
}
