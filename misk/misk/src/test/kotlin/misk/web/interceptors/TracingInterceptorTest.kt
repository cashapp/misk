package misk.web.interceptors

import com.google.inject.Guice
import io.opentracing.Tracer
import io.opentracing.mock.MockTracer
import misk.NetworkChain
import misk.NetworkInterceptor
import misk.asAction
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.MockTracingBackendModule
import misk.web.Get
import misk.web.Request
import misk.web.Response
import misk.web.actions.WebAction
import misk.web.actions.asNetworkChain
import okhttp3.HttpUrl
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jetty.http.HttpMethod
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class TracingInterceptorTest {
  @MiskTestModule
  val module = MockTracingBackendModule()

  @Inject private lateinit var tracingInterceptorFactory: TracingInterceptor.Factory
  @Inject private lateinit var tracingTestAction: TracingTestAction
  @Inject private lateinit var tracer: Tracer

  @Test
  fun initiatesTrace() {
    val request = Request(
        HttpUrl.parse("http://foo.bar/")!!,
        HttpMethod.GET,
        body = Buffer()
    )
    val tracingInterceptor = tracingInterceptorFactory.create(TracingTestAction::call.asAction())!!
    val chain = tracingTestAction.asNetworkChain(TracingTestAction::call, request,
        tracingInterceptor, TerminalInterceptor(200))

    @Suppress("UNCHECKED_CAST")
    chain.proceed(chain.request) as Response<String>

    val mockTracer = tracer as MockTracer
    assertThat(mockTracer.finishedSpans().size).isEqualTo(1)
  }

  @Test
  fun nothingIfNotInstalled() {
    val injector = Guice.createInjector()

    val tracingInterceptorFactory: TracingInterceptor.Factory =
        injector.getInstance(TracingInterceptor.Factory::class.java)

    assertThat(tracingInterceptorFactory.create(TracingTestAction::call.asAction())).isNull()
  }

  internal class TracingTestAction : WebAction {
    @Get("/trace")
    fun call(): Response<String> {
      return Response("success")
    }
  }

  internal class TerminalInterceptor(val status: Int) : NetworkInterceptor {
    override fun intercept(chain: NetworkChain): Response<*> = Response("foo", statusCode = status)
  }
}
