package misk.web.interceptors

import com.google.inject.Guice
import io.opentracing.tag.Tags
import misk.MiskTestingServiceModule
import misk.asAction
import misk.exceptions.WebActionException
import misk.inject.KAbstractModule
import misk.testing.ConcurrentMockTracer
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.MockTracingBackendModule
import misk.tracing.traceWithSpan
import misk.web.DispatchMechanism
import misk.web.FakeHttpCall
import misk.web.Get
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.RealNetworkChain
import misk.web.Response
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import javax.inject.Inject

@MiskTest(startService = true)
class TracingInterceptorTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var tracingInterceptorFactory: TracingInterceptor.Factory
  @Inject private lateinit var tracingTestAction: TracingTestAction
  @Inject private lateinit var tracer: ConcurrentMockTracer
  @Inject private lateinit var jettyService: JettyService

  companion object {
    // Not exposed: https://github.com/opentracing/opentracing-java/blob/2df2a8983e35dff23c4fb894e5f5ae3f98f1cf7b/opentracing-mock/src/main/java/io/opentracing/mock/MockTracer.java#L228-L229
    val SPAN_ID_KEY = "spanid"
    val TRACE_ID_KEY = "traceid"
  }

  @Test
  fun initiatesTrace() {
    val tracingInterceptor = tracingInterceptorFactory.create(
      TracingTestAction::call.asAction(DispatchMechanism.GET)
    )!!
    val httpCall = FakeHttpCall(url = "http://foo.bar".toHttpUrl())
    val chain = RealNetworkChain(
      TracingTestAction::call.asAction(DispatchMechanism.GET),
      tracingTestAction, httpCall, listOf(tracingInterceptor, TerminalInterceptor(200))
    )

    chain.proceed(chain.httpCall)

    val span = tracer.take("http.action")
    assertThat(span.parentId()).isEqualTo(0)
    assertThat(span.tags()).isEqualTo(
      mapOf(
        "resource.name" to "misk.web.interceptors.TracingInterceptorTest\$TracingTestAction",
        "http.method" to "GET",
        "http.status_code" to 200,
        "http.url" to "http://foo.bar/",
        "span.kind" to "server"
      )
    )
  }

  @Test
  fun looksForParentContext() {
    val tracingInterceptor = tracingInterceptorFactory.create(
      TracingTestAction::call.asAction(DispatchMechanism.GET)
    )!!
    val httpCall = FakeHttpCall(
      url = "http://foo.bar".toHttpUrl(),
      requestHeaders = headersOf(SPAN_ID_KEY, "1", TRACE_ID_KEY, "2")
    )
    val chain = RealNetworkChain(
      TracingTestAction::call.asAction(DispatchMechanism.GET),
      tracingTestAction, httpCall, listOf(tracingInterceptor, TerminalInterceptor(200))
    )

    chain.proceed(chain.httpCall)

    val span = tracer.take("http.action")
    assertThat(span.parentId()).isEqualTo(1)
  }

  @Test
  fun looksForParentSpan() {
    val tracingInterceptor = tracingInterceptorFactory.create(
      TracingTestAction::call.asAction(DispatchMechanism.GET)
    )!!
    val httpCall = FakeHttpCall(
      url = "http://foo.bar".toHttpUrl(),
      requestHeaders = headersOf(SPAN_ID_KEY, "1", TRACE_ID_KEY, "2")
    )
    val chain = RealNetworkChain(
      TracingTestAction::call.asAction(DispatchMechanism.GET),
      tracingTestAction, httpCall,
      listOf(
        object : NetworkInterceptor {
          override fun intercept(chain: NetworkChain) {
            tracer.traceWithSpan("parent-span-exists") {
              tracer.activeSpan().setBaggageItem("hello", "world")
              chain.proceed(chain.httpCall)
            }
          }
        },
        tracingInterceptor, TerminalInterceptor(200)
      )
    )

    chain.proceed(chain.httpCall)

    val span = tracer.take("http.action")
    assertThat(span.getBaggageItem("hello")).isEqualTo("world")
  }

  @Test
  fun failedTrace() {
    get("/failed_trace")

    val span = tracer.take("http.action")
    assertThat(span.tags().get(Tags.ERROR.key)).isEqualTo(true)
    assertThat(span.tags().get(Tags.HTTP_STATUS.key)).isEqualTo(400)
  }

  @Test
  fun failedTraceWithException() {
    get("/exception_trace")

    val span = tracer.take("http.action")
    assertThat(span.tags().get(Tags.ERROR.key)).isEqualTo(true)
    assertThat(span.tags().get(Tags.HTTP_STATUS.key)).isEqualTo(420)
  }

  @Test
  fun failedTraceWithParentSpan() {
    val tracingInterceptor = tracingInterceptorFactory.create(
      TracingTestAction::call.asAction(DispatchMechanism.GET)
    )!!
    val httpCall = FakeHttpCall(url = "http://foo.bar".toHttpUrl())
    val chain = RealNetworkChain(
      TracingTestAction::call.asAction(DispatchMechanism.GET),
      tracingTestAction, httpCall,
      listOf(
        object : NetworkInterceptor {
          override fun intercept(chain: NetworkChain) {
            tracer.traceWithSpan("parent-span") {
              chain.proceed(chain.httpCall)
            }
          }
        },
        tracingInterceptor, TerminalInterceptor(500)
      )
    )

    chain.proceed(chain.httpCall)

    val span = tracer.take("parent-span")
    assertThat(span.tags().get(Tags.ERROR.key)).isEqualTo(true)
  }

  @Test
  fun nothingIfNotInstalled() {
    val injector = Guice.createInjector()

    val tracingInterceptorFactory: TracingInterceptor.Factory =
      injector.getInstance(TracingInterceptor.Factory::class.java)

    assertThat(
      tracingInterceptorFactory.create(
        TracingTestAction::call.asAction(DispatchMechanism.GET)
      )
    ).isNull()
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
      return Response("no good", statusCode = HttpURLConnection.HTTP_BAD_REQUEST)
    }
  }

  internal class ExceptionThrowingTracingTestAction @Inject constructor() : WebAction {
    @Get("/exception_trace")
    fun call(): Response<String> {
      throw WebActionException(420, "Chill, man", "chiiiilll")
    }
  }

  internal class TerminalInterceptor(private val status: Int) : NetworkInterceptor {
    override fun intercept(chain: NetworkChain) {
      chain.httpCall.statusCode = status
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(MockTracingBackendModule())
      install(WebActionModule.create<TracingTestAction>())
      install(WebActionModule.create<FailedTracingTestAction>())
      install(WebActionModule.create<ExceptionThrowingTracingTestAction>())

      bind<TracingInterceptor.Factory>()
    }
  }
}
