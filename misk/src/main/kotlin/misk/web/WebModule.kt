package misk.web

import misk.ApplicationInterceptor
import misk.MiskDefault
import misk.exceptions.ActionException
import misk.inject.KAbstractModule
import misk.scope.ActionScopedProviderModule
import misk.security.ssl.CertificatesModule
import misk.web.exceptions.ActionExceptionMapper
import misk.web.exceptions.ExceptionHandlingInterceptor
import misk.web.exceptions.ExceptionMapperModule
import misk.web.extractors.FormValueParameterExtractorFactory
import misk.web.extractors.HeadersParameterExtractorFactory
import misk.web.extractors.ParameterExtractor
import misk.web.extractors.PathPatternParameterExtractorFactory
import misk.web.extractors.QueryStringParameterExtractorFactory
import misk.web.extractors.RequestBodyParameterExtractor
import misk.web.extractors.WebSocketParameterExtractorFactory
import misk.web.interceptors.InternalErrorInterceptorFactory
import misk.web.interceptors.MarshallerInterceptor
import misk.web.interceptors.MetricsInterceptor
import misk.web.interceptors.RequestLoggingInterceptor
import misk.web.interceptors.TracingInterceptor
import misk.web.jetty.JettyModule
import misk.web.marshal.JsonMarshaller
import misk.web.marshal.JsonUnmarshaller
import misk.web.marshal.MarshallerModule
import misk.web.marshal.PlainTextMarshaller
import misk.web.marshal.ProtobufMarshaller
import misk.web.marshal.ProtobufUnmarshaller
import misk.web.marshal.UnmarshallerModule
import misk.web.resources.StaticResourceInterceptor
import misk.web.resources.StaticResourceMapper
import javax.servlet.http.HttpServletRequest

class WebModule : KAbstractModule() {
  override fun configure() {
    install(JettyModule())
    install(object : ActionScopedProviderModule() {
      override fun configureProviders() {
        bindSeedData(Request::class)
        bindSeedData(HttpServletRequest::class)
      }
    })

    // Register built-in marshallers and unmarshallers
    install(MarshallerModule.create<PlainTextMarshaller.Factory>())
    install(MarshallerModule.create<JsonMarshaller.Factory>())
    install(MarshallerModule.create<ProtobufMarshaller.Factory>())
    install(UnmarshallerModule.create<JsonUnmarshaller.Factory>())
    install(UnmarshallerModule.create<ProtobufUnmarshaller.Factory>())

    // Create empty set binders of interceptor factories that can be added to by users.
    newMultibinder<NetworkInterceptor.Factory>()
    newMultibinder<ApplicationInterceptor.Factory>()

    // Register built-in interceptors. Interceptors run in the order in which they are
    // installed, and the order of these interceptors is critical.

    // Handle all unexpected errors that occur during dispatch
    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
        .to<InternalErrorInterceptorFactory>()

    // Optionally log request and response details
    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
        .to<RequestLoggingInterceptor.Factory>()

    // Collect metrics on the status of results and response times of requests
    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
        .to<MetricsInterceptor.Factory>()

    // Traces requests as they work their way through the system.
    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
        .to<TracingInterceptor.Factory>()

    // Convert and log application level exceptions into their appropriate response format
    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
        .to<ExceptionHandlingInterceptor.Factory>()

    // Convert typed responses into a ResponseBody that can marshal the response according to
    // the client's requested content-type
    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
        .to<MarshallerInterceptor.Factory>()

    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
        .to<StaticResourceInterceptor.Factory>()
    newMultibinder<StaticResourceMapper.Entry>()

    install(ExceptionMapperModule.create<ActionException, ActionExceptionMapper>())

    // Register built-in parameter extractors
    multibind<ParameterExtractor.Factory>()
        .toInstance(PathPatternParameterExtractorFactory)
    multibind<ParameterExtractor.Factory>()
        .toInstance(QueryStringParameterExtractorFactory)
    multibind<ParameterExtractor.Factory>()
        .toInstance(FormValueParameterExtractorFactory)
    multibind<ParameterExtractor.Factory>()
        .toInstance(HeadersParameterExtractorFactory)
    multibind<ParameterExtractor.Factory>()
        .toInstance(WebSocketParameterExtractorFactory)
    multibind<ParameterExtractor.Factory>()
        .to<RequestBodyParameterExtractor.Factory>()

    // Install infrastructure support
    install(CertificatesModule())

    // Bind _admin static resources to web
    binder().addMultibinderBinding<StaticResourceMapper.Entry>()
        .toInstance(StaticResourceMapper.Entry("/_admin/", "web/_admin", "misk/web/_admin/build"))
  }
}
