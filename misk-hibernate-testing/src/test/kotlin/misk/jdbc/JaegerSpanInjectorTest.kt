package misk.jdbc

import net.ttddyy.dsproxy.transform.TransformInfo
import org.junit.jupiter.api.Test
import io.opentracing.mock.MockTracer
import kotlin.test.assertEquals

class JaegerSpanInjectorTest {
   @Test fun parseQueryPlans() {
       val tracer = MockTracer()
       val buildSpan = tracer.buildSpan("test")
       val span = buildSpan.start()
       val scope = tracer.scopeManager().activate(span, true)
       val contextString = span.context().toString()

       val config = DataSourceConfig(DataSourceType.VITESS)
       val injector =  JaegerSpanInjector(tracer, config)
       val query = "SELECT * FROM table"
       val transformInfo = TransformInfo(null, null, query, false, 0)
       val result = injector.transformQuery(transformInfo)

       assertEquals("/*VT_SPAN_CONTEXT=$contextString*/$query", result)
       scope.close()
   }
}

