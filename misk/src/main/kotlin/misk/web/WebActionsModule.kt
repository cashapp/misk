package misk.web

import com.google.inject.AbstractModule
import misk.Interceptor
import misk.inject.newMultibinder
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
        binder().newMultibinder<Interceptor.Factory>().to<InternalErrorInterceptorFactory>()
        binder().newMultibinder<Interceptor.Factory>().to<RequestLoggingInterceptor.Factory>()
        binder().newMultibinder<Interceptor.Factory>().to<JsonInterceptorFactory>()
        binder().newMultibinder<Interceptor.Factory>().toInstance(PlaintextInterceptorFactory)
        binder().newMultibinder<Interceptor.Factory>().to<MetricsInterceptor.Factory>()
        binder().newMultibinder<Interceptor.Factory>().to<BoxResponseInterceptorFactory>()
        binder().newMultibinder<ParameterExtractor.Factory>().toInstance(PathPatternParameterExtractor)
        binder().newMultibinder<ParameterExtractor.Factory>().to<JsonBodyParameterExtractorFactory>()
        binder().newMultibinder<ParameterExtractor.Factory>().toInstance(HeadersParameterExtractorFactory)
    }
}
