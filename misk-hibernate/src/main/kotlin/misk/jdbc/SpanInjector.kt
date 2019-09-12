package misk.jdbc

import io.opentracing.Tracer
import io.opentracing.propagation.Format
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder
import net.ttddyy.dsproxy.transform.QueryTransformer
import net.ttddyy.dsproxy.transform.TransformInfo
import javax.sql.DataSource
import io.opentracing.propagation.TextMap
import java.lang.RuntimeException

/***
 * On the fly decorates queries with the span context, so the query call can be traced all the way through Vitess
 */
class SpanInjector(
  val tracer: Tracer?,
  val config: DataSourceConfig
) : QueryTransformer, DataSourceDecorator {
  override fun decorate(dataSource: DataSource): DataSource {
    if (config.type != DataSourceType.VITESS_MYSQL || tracer == null) return dataSource
    return ProxyDataSourceBuilder(dataSource).queryTransformer(this).build()
  }

  override fun transformQuery(transformInfo: TransformInfo?): String {
    val activeSpan = this.tracer?.activeSpan() ?: return transformInfo?.query!!

    return "/*VT_SPAN_CONTEXT=${getContextString(activeSpan.context())}*/${transformInfo?.query!!}"
  }

  private fun getContextString(activeSpan: io.opentracing.SpanContext): String {
    val carrier = StringBuilderCarrier()

    this.tracer!!.inject(activeSpan, Format.Builtin.TEXT_MAP, carrier)

    return carrier.toString()
  }

}

class StringBuilderCarrier : TextMap {
  val map = HashMap<String, String>()

  override fun iterator(): MutableIterator<MutableMap.MutableEntry<String, String>> {
    throw RuntimeException("not implemented")
  }

  override fun put(key: String?, value: String?) {
    if (key != null && value != null)
      map.put(key, value)
  }

  override fun toString(): String {
    return map.map { e -> e.key + "=" + e.value }.joinToString(separator = ":")
  }
}

