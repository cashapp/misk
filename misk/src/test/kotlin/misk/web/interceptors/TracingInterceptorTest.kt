package misk.web.interceptors

import com.google.inject.Guice
import io.opentracing.Tracer
import io.opentracing.mock.MockTracer
import misk.asAction
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.Response
import misk.web.actions.WebAction
import misk.web.actions.asChain
import org.assertj.core.api.Assertions.assertThat
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
    val tracingInterceptor = tracingInterceptorFactory.create(TracingTestAction::call.asAction())!!
    val chain = tracingTestAction.asChain(TracingTestAction::call, emptyList(), tracingInterceptor)

    @Suppress("UNCHECKED_CAST")
    chain.proceed(chain.args) as Response<String>

    val mockTracer = tracer as MockTracer
    assertThat(mockTracer.finishedSpans().size).isEqualTo(1)
  }

  @Test
  fun nothingIfNotInstalled() {
    val injector = Guice.createInjector()

    val tracingInterceptorFactory: TracingInterceptor.Factory = injector.getInstance(TracingInterceptor.Factory::class.java)

    assertThat(tracingInterceptorFactory.create(TracingTestAction::call.asAction())).isNull()
  }
}

internal class TracingTestAction : WebAction {
  @Get("/trace")
  fun call(): Response<String> {
    return Response("success")
  }
}

class MockTracingBackendModule : KAbstractModule() {
  override fun configure() {
    bind(Tracer::class.java).to(MockTracer::class.java).asEagerSingleton()
  }
}

class NoopModule : KAbstractModule() {
  override fun configure() {}
}
