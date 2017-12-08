package misk.web

import com.google.inject.AbstractModule
import misk.Interceptor
import misk.inject.addMultibinderBinding
import misk.inject.to
import misk.web.extractors.HeadersParameterExtractorFactory
import misk.web.extractors.JsonBodyParameterExtractorFactory
import misk.web.extractors.ParameterExtractor
import misk.web.extractors.PathPatternParameterExtractor
import misk.web.interceptors.BoxResponseInterceptorFactory
import misk.web.interceptors.InternalErrorInterceptorFactory
import misk.web.interceptors.JsonInterceptorFactory
import misk.web.interceptors.MetricsInterceptor
import misk.web.interceptors.PlaintextInterceptorFactory
import misk.web.interceptors.RequestLoggingInterceptor

class WebActionsModule : AbstractModule() {
    override fun configure() {
        binder().addMultibinderBinding<Interceptor.Factory>().to<InternalErrorInterceptorFactory>()
        binder().addMultibinderBinding<Interceptor.Factory>().to<RequestLoggingInterceptor.Factory>()
        binder().addMultibinderBinding<Interceptor.Factory>().to<JsonInterceptorFactory>()
        binder().addMultibinderBinding<Interceptor.Factory>().toInstance(PlaintextInterceptorFactory)
        binder().addMultibinderBinding<Interceptor.Factory>().to<MetricsInterceptor.Factory>()
        binder().addMultibinderBinding<Interceptor.Factory>().to<BoxResponseInterceptorFactory>()
        binder().addMultibinderBinding<ParameterExtractor.Factory>().toInstance(PathPatternParameterExtractor)
        binder().addMultibinderBinding<ParameterExtractor.Factory>().to<JsonBodyParameterExtractorFactory>()
        binder().addMultibinderBinding<ParameterExtractor.Factory>().toInstance(HeadersParameterExtractorFactory)
    }
}
