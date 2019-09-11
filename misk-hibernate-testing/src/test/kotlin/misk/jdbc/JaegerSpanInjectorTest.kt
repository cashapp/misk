package misk.jdbc

import net.ttddyy.dsproxy.transform.TransformInfo
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import io.opentracing.mock.MockTracer
import org.assertj.core.api.Assertions.assertThat
import javax.sql.DataSource

class JaegerSpanInjectorTest {
  @Test
  fun parseQueryPlans() {
    val tracer = MockTracer()
    val buildSpan = tracer.buildSpan("test")
    val span = buildSpan.start()
    val scope = tracer.scopeManager().activate(span, true)
    val contextString = span.context().toString()

    val config = DataSourceConfig(DataSourceType.VITESS)
    val injector = JaegerSpanInjector(tracer, config)
    val query = "SELECT * FROM table"
    val transformInfo = TransformInfo(null, null, query, false, 0)
    val result = injector.transformQuery(transformInfo)

    assertThat(result).isEqualTo("/*VT_SPAN_CONTEXT=$contextString*/$query")
    scope.close()
  }

  @Test
  fun testNotDecorateIfItsVitess() {
    val tracer = MockTracer()
    val config = DataSourceConfig(DataSourceType.VITESS)
    val injector = JaegerSpanInjector(tracer, config)
    val ds = Mockito.mock(DataSource::class.java)

    assertThat(injector.decorate(ds)).
        isSameAs(ds)
  }

  @Test
  fun testDecoratesIfItsVitessMysql() {
    val tracer = MockTracer()
    val config = DataSourceConfig(DataSourceType.VITESS_MYSQL)
    val injector = JaegerSpanInjector(tracer, config)
    val ds = Mockito.mock(DataSource::class.java)

    assertThat(injector.decorate(ds)).
        isNotSameAs(ds)
  }
}
