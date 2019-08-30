package misk.jdbc

import io.opentracing.Tracer
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder
import net.ttddyy.dsproxy.transform.QueryTransformer
import net.ttddyy.dsproxy.transform.TransformInfo
import javax.sql.DataSource

/***
 * On the fly decorates queries with the span context, so the query call can be traced all the way through Vitess
 */
class JaegerSpanInjector(val tracer: Tracer?,
                         val config: DataSourceConfig) : QueryTransformer, DataSourceDecorator {
    override fun decorate(dataSource: DataSource): DataSource {
        if (config.type != DataSourceType.VITESS || tracer == null) return dataSource
        return ProxyDataSourceBuilder(dataSource).queryTransformer(this).build()
    }

    override fun transformQuery(transformInfo: TransformInfo?): String {
        val activeSpan = this.tracer?.activeSpan()
        return if (activeSpan == null /* if we want to only allow jaeger here `|| activeSpan !is com.uber.jaeger.Span`*/) {
            transformInfo?.query!!
        } else {
            "/*VT_SPAN_CONTEXT=${activeSpan.context()}*/${transformInfo?.query!!}"
        }
    }
}