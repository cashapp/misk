package misk.web

import com.google.common.util.concurrent.Service
import misk.ApplicationInterceptor
import misk.MiskDefault
import misk.exceptions.ActionException
import misk.inject.KAbstractModule
import misk.metrics.web.MetricsJsonAction
import misk.scope.ActionScopedProviderModule
import misk.security.ssl.CertificatesModule
import misk.web.actions.AdminTabAction
import misk.web.actions.InternalErrorAction
import misk.web.actions.LivenessCheckAction
import misk.web.actions.NotFoundAction
import misk.web.actions.ReadinessCheckAction
import misk.web.actions.StatusAction
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
import misk.web.jetty.JettyService
import misk.web.marshal.JsonMarshaller
import misk.web.marshal.JsonUnmarshaller
import misk.web.marshal.Marshaller
import misk.web.marshal.PlainTextMarshaller
import misk.web.marshal.ProtobufMarshaller
import misk.web.marshal.ProtobufUnmarshaller
import misk.web.marshal.Unmarshaller
import misk.web.resources.StaticResourceInterceptor
import misk.web.resources.StaticResourceMapper
import javax.servlet.http.HttpServletRequest
import java.security.Provider as SecurityProvider

class WebModule : KAbstractModule() {
  override fun configure() {
    multibind<Service>().to<JettyService>()
    multibind<Service>().to<ConscryptService>()
    bind<SecurityProvider>()
        .annotatedWith(ForConscrypt::class.java)
        .toProvider(ConscryptService::class.java)

    install(object : ActionScopedProviderModule() {
      override fun configureProviders() {
        bindSeedData(Request::class)
        bindSeedData(HttpServletRequest::class)
      }
    })

    // Register built-in marshallers and unmarshallers
    multibind<Marshaller.Factory>().to<PlainTextMarshaller.Factory>()
    multibind<Marshaller.Factory>().to<JsonMarshaller.Factory>()
    multibind<Marshaller.Factory>().to<ProtobufMarshaller.Factory>()
    multibind<Unmarshaller.Factory>().to<JsonUnmarshaller.Factory>()
    multibind<Unmarshaller.Factory>().to<ProtobufUnmarshaller.Factory>()

    // Initialize empty sets for our multibindings.
    newMultibinder<NetworkInterceptor.Factory>()
    newMultibinder<ApplicationInterceptor.Factory>()
    newMultibinder<StaticResourceMapper.Entry>()

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

    // Add _admin installed tabs / forwarding mappings that don't have endpoints
    install(AdminTabModule())

    // Bind _admin static resources to web
    multibind<StaticResourceMapper.Entry>()
        .toInstance(StaticResourceMapper.Entry("/_admin/", "web/_admin", "misk/web/_admin/build"))

    // Bind build-in actions.
    multibind<WebActionEntry>().toInstance(WebActionEntry(AdminTabAction::class))
    multibind<WebActionEntry>().toInstance(WebActionEntry(MetricsJsonAction::class))
    multibind<WebActionEntry>().toInstance(WebActionEntry(InternalErrorAction::class))
    multibind<WebActionEntry>().toInstance(WebActionEntry(StatusAction::class))
    multibind<WebActionEntry>().toInstance(WebActionEntry(ReadinessCheckAction::class))
    multibind<WebActionEntry>().toInstance(WebActionEntry(LivenessCheckAction::class))
    multibind<WebActionEntry>().toInstance(WebActionEntry(NotFoundAction::class))

    // Make CORS wide-open.
    // TODO(adrw): this is not suitable for production. lock this down.
    multibind<NetworkInterceptor.Factory>().to<WideOpenDevelopmentInterceptorFactory>()
  }
}
