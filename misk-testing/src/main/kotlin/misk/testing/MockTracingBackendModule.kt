package misk.testing

import io.opentracing.Tracer
import io.opentracing.mock.MockTracer
import misk.inject.KAbstractModule

class MockTracingBackendModule : KAbstractModule() {
  override fun configure() {
    // TODO(keefer): Update to use wisp-tracing's ConcurrentMockTracer instead.
    bind<MockTracer>().to<ConcurrentMockTracer>()
    bind<Tracer>().to<ConcurrentMockTracer>()
  }
}
