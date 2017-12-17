package misk.web

import misk.Interceptor
import misk.MiskDefault
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.inject.addMultibinderBindingWithAnnotation
import misk.inject.to
import misk.scope.ActionScopedProviderModule
import misk.web.extractors.HeadersParameterExtractorFactory
import misk.web.extractors.ParameterExtractor
import misk.web.extractors.PathPatternParameterExtractorFactory
import misk.web.extractors.RequestBodyParameterExtractor
import misk.web.interceptors.BoxResponseInterceptorFactory
import misk.web.interceptors.InternalErrorInterceptorFactory
import misk.web.interceptors.MarshallerInterceptor
import misk.web.interceptors.MetricsInterceptor
import misk.web.interceptors.RequestLoggingInterceptor
import misk.web.jetty.JettyModule
import javax.servlet.http.HttpServletRequest
import misk.web.marshal.JsonMarshaller
import misk.web.marshal.JsonUnmarshaller
import misk.web.marshal.MarshallerModule
import misk.web.marshal.PlainTextMarshaller
import misk.web.marshal.UnmarshallerModule

class WebModule : KAbstractModule() {
  override fun configure() {
    install(JettyModule())
    install(object: ActionScopedProviderModule() {
      override fun configureProviders() {
        bindSeedData(Request::class)
        bindSeedData(HttpServletRequest::class)
      }
    })

    // Register built-in marshallers and unmarshallers
    install(MarshallerModule.create<PlainTextMarshaller.Factory>())
    install(MarshallerModule.create<JsonMarshaller.Factory>())
    install(UnmarshallerModule.create<JsonUnmarshaller.Factory>())

    // Create an empty set binder of interceptor factories that can be added to by users.
    newSetBinder<Interceptor.Factory>()

    // Register built-in interceptors
    binder().addMultibinderBindingWithAnnotation<Interceptor.Factory, MiskDefault>().to<InternalErrorInterceptorFactory>()
    binder().addMultibinderBindingWithAnnotation<Interceptor.Factory, MiskDefault>().to<RequestLoggingInterceptor.Factory>()
    binder().addMultibinderBindingWithAnnotation<Interceptor.Factory, MiskDefault>().to<MetricsInterceptor.Factory>()
    binder().addMultibinderBindingWithAnnotation<Interceptor.Factory, MiskDefault>().to<MarshallerInterceptor.Factory>()
    binder().addMultibinderBindingWithAnnotation<Interceptor.Factory, MiskDefault>().to<BoxResponseInterceptorFactory>()

    // Register built-in parameter extractors
    binder().addMultibinderBinding<ParameterExtractor.Factory>()
        .toInstance(PathPatternParameterExtractorFactory)
    binder().addMultibinderBinding<ParameterExtractor.Factory>()
        .toInstance(HeadersParameterExtractorFactory)
    binder().addMultibinderBinding<ParameterExtractor.Factory>()
        .to<RequestBodyParameterExtractor.Factory>()
  }
}
