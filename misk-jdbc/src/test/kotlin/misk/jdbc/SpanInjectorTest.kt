package misk.jdbc

import io.opentracing.mock.MockTracer
import io.opentracing.util.GlobalTracer
import net.ttddyy.dsproxy.transform.TransformInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpanInjectorTest {

  private val tracer = MockTracer()

  @BeforeAll
  fun initClass() {
    GlobalTracer.registerIfAbsent(tracer)
  }

  @BeforeEach
  fun setup() {
    tracer.reset()
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
    val tracer = GlobalTracer.get()
    val span = tracer.buildSpan("operation").start()
    tracer.activateSpan(span).use { _ ->
      val config = DataSourceConfig(DataSourceType.VITESS_MYSQL)
      val injector = SpanInjector(tracer, config)
      val query = "SELECT * FROM table"
      val transformInfo = TransformInfo(null, null, query, false, 0)
      val result = injector.transformQuery(transformInfo)
      assertThat(result).contains(span.context().toTraceId())
    }
  }
}
