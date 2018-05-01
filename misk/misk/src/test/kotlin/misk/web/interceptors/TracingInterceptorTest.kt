package misk.web.interceptors

import com.google.inject.Guice
import com.google.inject.util.Modules
import io.opentracing.Tracer
import io.opentracing.mock.MockTracer
import io.opentracing.tag.Tags
import misk.MiskModule
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.asAction
import misk.exceptions.ActionException
import misk.exceptions.StatusCode
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.MockTracingBackendModule
import misk.testing.TestWebModule
import misk.web.Get
import misk.web.Request
import misk.web.Response
import misk.web.WebActionModule
import misk.web.WebModule
import misk.web.actions.WebAction
import misk.web.actions.asNetworkChain
import misk.web.jetty.JettyService
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jetty.http.HttpMethod
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class TracingInterceptorTest {
  @MiskTestModule
  val module = Modules.combine(
      MockTracingBackendModule(),
      MiskModule(),
      WebModule(),
      TestWebModule(),
      TestModule())

  @Inject private lateinit var tracingInterceptorFactory: TracingInterceptor.Factory
  @Inject private lateinit var tracingTestAction: TracingTestAction
  @Inject private lateinit var tracer: Tracer
  @Inject private lateinit var jettyService: JettyService

  @Test
  fun initiatesTrace() {
    val tracingInterceptor = tracingInterceptorFactory.create(TracingTestAction::call.asAction())!!
    val request = Request(
        HttpUrl.parse("http://foo.bar/")!!,
        HttpMethod.GET,
        body = Buffer()
    )
    val chain = tracingTestAction.asNetworkChain(TracingTestAction::call, request,
        tracingInterceptor, TerminalInterceptor(200))

    chain.proceed(chain.request)

    val mockTracer = tracer as MockTracer
    assertThat(mockTracer.finishedSpans().size).isEqualTo(1)
    assertThat(mockTracer.finishedSpans().first().parentId()).isEqualTo(0)
    val span = mockTracer.finishedSpans().first()
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
    val request = Request(
        HttpUrl.parse("http://foo.bar/")!!,
        HttpMethod.GET,
        Headers.Builder().add("spanid", "1").add("traceid", "2").build(),
        body = Buffer()
    )
    val chain = tracingTestAction.asNetworkChain(TracingTestAction::call, request,
        tracingInterceptor, TerminalInterceptor(200))

    chain.proceed(chain.request)

    val mockTracer = tracer as MockTracer
    assertThat(mockTracer.finishedSpans().size).isEqualTo(1)
    assertThat(mockTracer.finishedSpans().first().parentId()).isEqualTo(1)
  }

  @Test
  fun failedTrace() {
    get("/failed_trace")

    val mockTracer = tracer as MockTracer
    assertThat(mockTracer.finishedSpans().size).isEqualTo(1)

    val span = mockTracer.finishedSpans().first()
    assertThat(span.tags().get(Tags.ERROR.key)).isEqualTo(true)
    assertThat(span.tags().get(Tags.HTTP_STATUS.key)).isEqualTo(400)
  }

  @Test
  fun failedTraceWithException() {
    get("/exception_trace")

    val mockTracer = tracer as MockTracer
    assertThat(mockTracer.finishedSpans().size).isEqualTo(1)

    val span = mockTracer.finishedSpans().first()
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

  internal class TracingTestAction : WebAction {
    @Get("/trace")
    fun call(): Response<String> {
      return Response("success")
    }
  }

  internal class FailedTracingTestAction : WebAction {
    @Get("/failed_trace")
    fun call(): Response<String> {
      return Response("no good", statusCode = StatusCode.BAD_REQUEST.code)
    }
  }

  internal class ExceptionThrowingTracingTestAction : WebAction {
    @Get("/exception_trace")
    fun call(): Response<String> {
      throw ActionException(StatusCode.ENHANCE_YOUR_CALM, "Chill, man")
    }
  }

  internal class TerminalInterceptor(val status: Int) : NetworkInterceptor {
    override fun intercept(chain: NetworkChain): Response<*> = Response("foo", statusCode = status)
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebActionModule.create<FailedTracingTestAction>())
      install(WebActionModule.create<ExceptionThrowingTracingTestAction>())
    }
  }
}

