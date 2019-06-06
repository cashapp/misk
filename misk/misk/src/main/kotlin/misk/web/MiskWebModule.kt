package misk.web

import com.google.inject.Provides
import com.google.inject.TypeLiteral
import misk.ApplicationInterceptor
import misk.MiskCaller
import misk.MiskDefault
import misk.ServiceModule
import misk.exceptions.ActionException
import misk.grpc.GrpcMarshallerFactory
import misk.grpc.GrpcUnmarshallerFactory
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
import misk.web.exceptions.ActionExceptionLogLevelConfig
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
import misk.web.interceptors.MetricsInterceptor
import misk.web.interceptors.RebalancingInterceptor
import misk.web.interceptors.RequestLogContextInterceptor
import misk.web.interceptors.RequestLoggingInterceptor
import misk.web.interceptors.TracingInterceptor
import misk.web.jetty.JettyConnectionMetricsCollector
import misk.web.jetty.JettyService
import misk.web.jetty.JettyThreadPoolMetricsCollector
import misk.web.marshal.JsonMarshaller
import misk.web.marshal.JsonUnmarshaller
import misk.web.marshal.Marshaller
import misk.web.marshal.PlainTextMarshaller
import misk.web.marshal.ProtobufMarshaller
import misk.web.marshal.ProtobufUnmarshaller
import misk.web.marshal.Unmarshaller
import misk.web.proxy.WebProxyEntry
import misk.web.resources.StaticResourceEntry
import org.eclipse.jetty.util.thread.QueuedThreadPool
import javax.inject.Inject
import javax.inject.Singleton
import javax.servlet.http.HttpServletRequest
import java.security.Provider as SecurityProvider

class MiskWebModule(private val config: WebConfig) : KAbstractModule() {
  override fun configure() {
    bind<WebConfig>().toInstance(config)
    bind<ActionExceptionLogLevelConfig>().toInstance(config.action_exception_log_level)

    install(ServiceModule<JettyService>())
    install(ServiceModule<JettyThreadPoolMetricsCollector>())
    install(ServiceModule<JettyConnectionMetricsCollector>())

    // Install support for accessing the current request and caller as ActionScoped types
    install(object : ActionScopedProviderModule() {
      override fun configureProviders() {
        bindSeedData(HttpCall::class)
        bindSeedData(HttpServletRequest::class)
        bindProvider(miskCallerType, MiskCallerProvider::class)
        newMultibinder<MiskCallerAuthenticator>()
      }
    })

    // Register built-in marshallers and unmarshallers
    multibind<Marshaller.Factory>().to<PlainTextMarshaller.Factory>()
    multibind<Marshaller.Factory>().to<JsonMarshaller.Factory>()
    multibind<Marshaller.Factory>().to<ProtobufMarshaller.Factory>()
    multibind<Marshaller.Factory>().to<GrpcMarshallerFactory>()
    multibind<Unmarshaller.Factory>().to<JsonUnmarshaller.Factory>()
    multibind<Unmarshaller.Factory>().to<ProtobufUnmarshaller.Factory>()
    multibind<Unmarshaller.Factory>().to<GrpcUnmarshallerFactory>()

    // Initialize empty sets for our multibindings.
    newMultibinder<NetworkInterceptor.Factory>()
    newMultibinder<ApplicationInterceptor.Factory>()
    newMultibinder<StaticResourceEntry>()
    newMultibinder<WebProxyEntry>()

    // Register built-in interceptors. Interceptors run in the order in which they are
    // installed, and the order of these interceptors is critical.

    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
        .to<RebalancingInterceptor.Factory>()

    // Handle all unexpected errors that occur during dispatch
    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
        .to<InternalErrorInterceptorFactory>()

    // Add request related fields to MDC for logging
    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
        .to<RequestLogContextInterceptor.Factory>()

    // Collect metrics on the status of results and response times of requests
    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
        .to<MetricsInterceptor.Factory>()

    // Traces requests as they work their way through the system.
    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
        .to<TracingInterceptor.Factory>()

    // Convert and log application level exceptions into their appropriate response format
    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
        .to<ExceptionHandlingInterceptor.Factory>()

    // Optionally log request and response details
    multibind<ApplicationInterceptor.Factory>(MiskDefault::class)
        .to<RequestLoggingInterceptor.Factory>()

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
    install(WebActionModule.create<InternalErrorAction>())
    install(WebActionModule.create<StatusAction>())
    install(WebActionModule.create<ReadinessCheckAction>())
    install(WebActionModule.create<LivenessCheckAction>())
    install(WebActionModule.create<NotFoundAction>())
  }

  @Provides @Singleton
  fun provideJettyThreadPool(): QueuedThreadPool {
    return config.jetty_max_thread_pool_size?.let { QueuedThreadPool(it) } ?: QueuedThreadPool()
  }

  class MiskCallerProvider @Inject constructor(
    private val authenticators: List<MiskCallerAuthenticator>
  ): ActionScopedProvider<MiskCaller?> {
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
