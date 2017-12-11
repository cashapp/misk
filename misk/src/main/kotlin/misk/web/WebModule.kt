package misk.web

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import misk.Interceptor
import misk.MiskDefault
import misk.inject.addMultibinderBinding
import misk.inject.addMultibinderBindingWithAnnotation
import misk.inject.to
import misk.web.extractors.HeadersParameterExtractorFactory
import misk.web.extractors.JsonBodyParameterExtractorFactory
import misk.web.extractors.ParameterExtractor
import misk.web.extractors.PathPatternParameterExtractorFactory
import misk.web.interceptors.BoxResponseInterceptorFactory
import misk.web.interceptors.InternalErrorInterceptorFactory
import misk.web.interceptors.JsonInterceptorFactory
import misk.web.interceptors.MetricsInterceptor
import misk.web.interceptors.PlaintextInterceptorFactory
import misk.web.interceptors.RequestLoggingInterceptor
import misk.web.jetty.JettyModule

class WebModule : AbstractModule() {
  override fun configure() {
    install(JettyModule())

    // Create an empty set binder of interceptor factories that can be added to by users.
    Multibinder.newSetBinder(binder(), Interceptor.Factory::class.java)

    binder().addMultibinderBindingWithAnnotation<Interceptor.Factory, MiskDefault>().to<InternalErrorInterceptorFactory>()
    binder().addMultibinderBindingWithAnnotation<Interceptor.Factory, MiskDefault>().to<RequestLoggingInterceptor.Factory>()
    binder().addMultibinderBindingWithAnnotation<Interceptor.Factory, MiskDefault>().to<JsonInterceptorFactory>()
    binder().addMultibinderBindingWithAnnotation<Interceptor.Factory, MiskDefault>().toInstance(PlaintextInterceptorFactory)
    binder().addMultibinderBindingWithAnnotation<Interceptor.Factory, MiskDefault>().to<MetricsInterceptor.Factory>()
    binder().addMultibinderBindingWithAnnotation<Interceptor.Factory, MiskDefault>().to<BoxResponseInterceptorFactory>()

    binder().addMultibinderBinding<ParameterExtractor.Factory>().toInstance(PathPatternParameterExtractorFactory)
    binder().addMultibinderBinding<ParameterExtractor.Factory>().to<JsonBodyParameterExtractorFactory>()
    binder().addMultibinderBinding<ParameterExtractor.Factory>().toInstance(HeadersParameterExtractorFactory)
  }
}
