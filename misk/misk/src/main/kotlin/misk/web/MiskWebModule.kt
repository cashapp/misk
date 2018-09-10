package misk.web

import com.google.common.util.concurrent.Service
import com.google.inject.TypeLiteral
import misk.ApplicationInterceptor
import misk.MiskCaller
import misk.MiskDefault
import misk.exceptions.ActionException
import misk.inject.KAbstractModule
import misk.scope.ActionScopedProvider
import misk.scope.ActionScopedProviderModule
import misk.security.authz.MiskCallerAuthenticator
import misk.security.ssl.CertificatesModule
import misk.web.actions.InternalErrorAction
import misk.web.actions.LivenessCheckAction
import misk.web.actions.NotFoundAction
import misk.web.actions.ReadinessCheckAction
import misk.web.actions.StatusAction
import misk.web.actions.WebActionEntry
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
import misk.web.interceptors.RequestLogContextInterceptor
import misk.web.interceptors.RequestLoggingInterceptor
import misk.web.interceptors.TracingInterceptor
import misk.web.interceptors.WideOpenDevelopmentInterceptorFactory
import misk.web.jetty.JettyService
import misk.web.marshal.GrpcMarshaller
import misk.web.marshal.GrpcUnmarshaller
import misk.web.marshal.JsonMarshaller
import misk.web.marshal.JsonUnmarshaller
import misk.web.marshal.Marshaller
import misk.web.marshal.PlainTextMarshaller
import misk.web.marshal.ProtobufMarshaller
import misk.web.marshal.ProtobufUnmarshaller
import misk.web.marshal.Unmarshaller
import misk.web.resources.StaticResourceEntry
import javax.inject.Inject
import javax.servlet.http.HttpServletRequest
import java.security.Provider as SecurityProvider

class MiskWebModule : KAbstractModule() {
  override fun configure() {
    multibind<Service>().to<JettyService>()
    multibind<Service>().to<ConscryptService>()
    bind<SecurityProvider>()
        .annotatedWith(ForConscrypt::class.java)
        .toProvider(ConscryptService::class.java)

    // Install support for accessing the current request and caller as ActionScoped types
    install(object : ActionScopedProviderModule() {
      override fun configureProviders() {
        bindSeedData(Request::class)
        bindSeedData(HttpServletRequest::class)
        bindProvider(miskCallerType, MiskCallerProvider::class)
        newMultibinder<MiskCallerAuthenticator>()
      }
    })

    // Register built-in marshallers and unmarshallers
    multibind<Marshaller.Factory>().to<PlainTextMarshaller.Factory>()
    multibind<Marshaller.Factory>().to<JsonMarshaller.Factory>()
    multibind<Marshaller.Factory>().to<ProtobufMarshaller.Factory>()
    multibind<Marshaller.Factory>().to<GrpcMarshaller.Factory>()
    multibind<Unmarshaller.Factory>().to<JsonUnmarshaller.Factory>()
    multibind<Unmarshaller.Factory>().to<ProtobufUnmarshaller.Factory>()
    multibind<Unmarshaller.Factory>().to<GrpcUnmarshaller.Factory>()

    // Initialize empty sets for our multibindings.
    newMultibinder<NetworkInterceptor.Factory>()
    newMultibinder<ApplicationInterceptor.Factory>()
    newMultibinder<StaticResourceEntry>()

    // Register built-in interceptors. Interceptors run in the order in which they are
    // installed, and the order of these interceptors is critical.

    // Handle all unexpected errors that occur during dispatch
    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
        .to<InternalErrorInterceptorFactory>()

    // Add request related fields to MDC for logging
    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
        .to<RequestLogContextInterceptor.Factory>()

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

    install(ExceptionMapperModule.create<ActionException, ActionExceptionMapper>())

    // Register built-in parameter extractors
    multibind<ParameterExtractor.Factory>().toInstance(PathPatternParameterExtractorFactory)
    multibind<ParameterExtractor.Factory>().toInstance(QueryStringParameterExtractorFactory)
    multibind<ParameterExtractor.Factory>().toInstance(FormValueParameterExtractorFactory)
    multibind<ParameterExtractor.Factory>().toInstance(HeadersParameterExtractorFactory)
    multibind<ParameterExtractor.Factory>().toInstance(WebSocketParameterExtractorFactory)
    multibind<ParameterExtractor.Factory>().to<RequestBodyParameterExtractor.Factory>()

    // Install infrastructure support
    install(CertificatesModule())

    // Bind build-in actions.
    multibind<WebActionEntry>().toInstance(WebActionEntry<InternalErrorAction>())
    multibind<WebActionEntry>().toInstance(WebActionEntry<StatusAction>())
    multibind<WebActionEntry>().toInstance(WebActionEntry<ReadinessCheckAction>())
    multibind<WebActionEntry>().toInstance(WebActionEntry<LivenessCheckAction>())
    multibind<WebActionEntry>().toInstance(WebActionEntry<NotFoundAction>())

    // Adds open CORS headers in development to allow through API calls from webpack servers
    multibind<NetworkInterceptor.Factory>().to<WideOpenDevelopmentInterceptorFactory>()
  }

  class MiskCallerProvider : ActionScopedProvider<MiskCaller?> {
    @Inject lateinit var authenticators: List<MiskCallerAuthenticator>

    override fun get(): MiskCaller? {
      return authenticators.mapNotNull {
        it.getAuthenticatedCaller()
      }.firstOrNull()
    }
  }

  private companion object {
    val miskCallerType = object : TypeLiteral<MiskCaller?>() {}
  }
}
