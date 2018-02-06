package misk.web

import misk.Interceptor
import misk.MiskDefault
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.inject.addMultibinderBindingWithAnnotation
import misk.inject.to
import misk.scope.ActionScopedProviderModule
import misk.web.exceptions.ActionExceptionMapper
import misk.web.exceptions.ExceptionHandlingInterceptor
import misk.web.exceptions.ExceptionMapperModule
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
import misk.web.marshal.JsonMarshaller
import misk.web.marshal.JsonUnmarshaller
import misk.web.marshal.MarshallerModule
import misk.web.marshal.PlainTextMarshaller
import misk.web.marshal.UnmarshallerModule
import javax.servlet.http.HttpServletRequest

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

        // Register built-in interceptors. Interceptors run in the order in which they are
        // installed, and the order of these interceptors is critical.

        // Handle all unexpected errors that occur during dispatch
        binder().addMultibinderBindingWithAnnotation<Interceptor.Factory, MiskDefault>().to<InternalErrorInterceptorFactory>()

        // Optionally log request and response details
        binder().addMultibinderBindingWithAnnotation<Interceptor.Factory, MiskDefault>().to<RequestLoggingInterceptor.Factory>()

        // Collect metrics on the status of results and response times of requests
        binder().addMultibinderBindingWithAnnotation<Interceptor.Factory, MiskDefault>().to<MetricsInterceptor.Factory>()

        // Convert and log application level exceptions into their appropriate response format
        binder().addMultibinderBindingWithAnnotation<Interceptor.Factory, MiskDefault>().to<ExceptionHandlingInterceptor.Factory>()

        // Convert typed responses into a ResponseBody that can marshal the response according to
        // the client's requested content-typ
        binder().addMultibinderBindingWithAnnotation<Interceptor.Factory, MiskDefault>().to<MarshallerInterceptor.Factory>()

        // Wrap "raw" responses with a Response object
        binder().addMultibinderBindingWithAnnotation<Interceptor.Factory, MiskDefault>().to<BoxResponseInterceptorFactory>()

        // Register build-in exception mappers
        install(ExceptionMapperModule.create<ActionExceptionMapper>())

        // Register built-in parameter extractors
        binder().addMultibinderBinding<ParameterExtractor.Factory>()
                .toInstance(PathPatternParameterExtractorFactory)
        binder().addMultibinderBinding<ParameterExtractor.Factory>()
                .toInstance(HeadersParameterExtractorFactory)
        binder().addMultibinderBinding<ParameterExtractor.Factory>()
                .to<RequestBodyParameterExtractor.Factory>()
    }
}
