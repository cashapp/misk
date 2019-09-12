package misk.jdbc

import datadog.opentracing.DDSpan
import io.opentracing.Tracer
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder
import net.ttddyy.dsproxy.transform.QueryTransformer
import net.ttddyy.dsproxy.transform.TransformInfo
import javax.sql.DataSource

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
    val activeSpan = this.tracer?.activeSpan()

    val s = when (activeSpan) {
      null -> null
      is DDSpan -> getContextString(activeSpan)
      else -> activeSpan.context().toString()
    }

    return if (s == null) {
      transformInfo?.query!!
    } else {
      "/*VT_SPAN_CONTEXT=${s}*/${transformInfo?.query!!}"
    }
  }

  private fun getContextString(activeSpan: DDSpan): String {
    val ctx = activeSpan.context()
    val s = ctx.traceId + ":" + ctx.parentId + ":" + ctx.samplingPriority

    return if (ctx.origin != null) s + ":" + ctx.origin else s
  }

}