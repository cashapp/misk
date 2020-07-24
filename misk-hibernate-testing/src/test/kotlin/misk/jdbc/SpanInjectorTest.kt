package misk.jdbc

import datadog.opentracing.DDSpan
import datadog.opentracing.DDTracer
import datadog.trace.common.writer.Writer
import net.ttddyy.dsproxy.transform.TransformInfo
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import io.opentracing.mock.MockTracer
import org.assertj.core.api.Assertions.assertThat
import javax.sql.DataSource

class SpanInjectorTest {
  @Test
  fun testNotDecorateIfItsVitess() {
    val tracer = MockTracer()
    val config = DataSourceConfig(DataSourceType.VITESS)
    val injector = SpanInjector(tracer, config)
    val ds = Mockito.mock(DataSource::class.java)

    assertThat(injector.decorate(ds)).isSameAs(ds)
  }

  @Test
  fun testDecoratesIfItsVitessMysql() {
    val tracer = MockTracer()
    val config = DataSourceConfig(DataSourceType.VITESS_MYSQL)
    val injector = SpanInjector(tracer, config)
    val ds = Mockito.mock(DataSource::class.java)

    assertThat(injector.decorate(ds)).isNotSameAs(ds)
  }

  @Test
  fun testDatadog() {
    val tracer = DDTracer.builder()
        .writer(NoopWriter())
        .build();
    val span = tracer.buildSpan("operation").start()
    tracer.activateSpan(span).use { _ ->
      val config = DataSourceConfig(DataSourceType.VITESS_MYSQL)
      val injector = SpanInjector(tracer, config)
      val query = "SELECT * FROM table"
      val transformInfo = TransformInfo(null, null, query, false, 0)
      val result = injector.transformQuery(transformInfo)
      assertThat(result).contains((span as DDSpan).traceId.toString())
    }
  }
}

class NoopWriter : Writer {
  override fun start() {
  }

  override fun write(trace: MutableList<DDSpan>?) {
  }

  override fun close() {
  }

  override fun incrementTraceCount() {
  }
}
