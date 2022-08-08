package misk.web

import com.google.common.util.concurrent.Service
import com.google.inject.Key
import com.google.inject.Provider
import com.google.inject.Provides
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.servlet.http.HttpServletRequest
import misk.ApplicationInterceptor
import misk.MiskCaller
import misk.MiskDefault
import misk.ServiceModule
import misk.exceptions.WebActionException
import misk.grpc.GrpcFeatureBinding
import misk.inject.KAbstractModule
import misk.inject.toKey
import misk.queuing.TimedBlockingQueue
import misk.scope.ActionScopedProvider
import misk.scope.ActionScopedProviderModule
import misk.security.authz.MiskCallerAuthenticator
import misk.security.ssl.CertificatesModule
import misk.web.actions.LivenessCheckAction
import misk.web.actions.NotFoundAction
import misk.web.actions.ReadinessCheckAction
import misk.web.actions.StatusAction
import misk.web.exceptions.ActionExceptionLogLevelConfig
import misk.web.exceptions.EofExceptionMapper
import misk.web.exceptions.ExceptionHandlingInterceptor
import misk.web.exceptions.ExceptionMapperModule
import misk.web.exceptions.IOExceptionMapper
import misk.web.exceptions.RequestBodyExceptionMapper
import misk.web.exceptions.WebActionExceptionMapper
import misk.web.extractors.FormValueFeatureBinding
import misk.web.extractors.PathParamFeatureBinding
import misk.web.extractors.QueryParamFeatureBinding
import misk.web.extractors.RequestBodyException
import misk.web.extractors.RequestBodyFeatureBinding
import misk.web.extractors.RequestHeadersFeatureBinding
import misk.web.extractors.ResponseBodyFeatureBinding
import misk.web.extractors.WebSocketFeatureBinding
import misk.web.extractors.WebSocketListenerFeatureBinding
import misk.web.interceptors.BeforeContentEncoding
import misk.web.interceptors.ConcurrencyLimiterFactory
import misk.web.interceptors.ConcurrencyLimitsInterceptor
import misk.web.interceptors.ForContentEncoding
import misk.web.interceptors.GunzipRequestBodyInterceptor
import misk.web.interceptors.InternalErrorInterceptorFactory
import misk.web.interceptors.MetricsInterceptor
import misk.web.interceptors.RebalancingInterceptor
import misk.web.interceptors.RequestBodyLoggingInterceptor
import misk.web.interceptors.RequestLogContextInterceptor
import misk.web.interceptors.RequestLoggingInterceptor
import misk.web.interceptors.TracingInterceptor
import misk.web.jetty.JettyConnectionMetricsCollector
import misk.web.jetty.JettyService
import misk.web.jetty.JettyThreadPoolMetricsCollector
import misk.web.jetty.MeasuredQueuedThreadPool
import misk.web.jetty.MeasuredThreadPool
import misk.web.jetty.MeasuredThreadPoolExecutor
import misk.web.jetty.ThreadPoolQueueMetrics
import misk.web.marshal.JsonMarshaller
import misk.web.marshal.JsonUnmarshaller
import misk.web.marshal.Marshaller
import misk.web.marshal.MultipartUnmarshaller
import misk.web.marshal.PlainTextMarshaller
import misk.web.marshal.ProtobufMarshaller
import misk.web.marshal.ProtobufUnmarshaller
import misk.web.marshal.Unmarshaller
import misk.web.mdc.LogContextProvider
import misk.web.mdc.RequestHttpMethodLogContextProvider
import misk.web.mdc.RequestProtocolLogContextProvider
import misk.web.mdc.RequestRemoteAddressLogContextProvider
import misk.web.mdc.RequestURILogContextProvider
import misk.web.proxy.WebProxyEntry
import misk.web.resources.StaticResourceEntry
import org.eclipse.jetty.io.EofException
import org.eclipse.jetty.server.handler.StatisticsHandler
import org.eclipse.jetty.server.handler.gzip.GzipHandler
import org.eclipse.jetty.util.thread.ExecutorThreadPool
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.eclipse.jetty.util.thread.ThreadPool

class MiskWebModule(
  private val config: WebConfig,
  private val jettyDependsOn: List<Key<out Service>> = emptyList(),
) : KAbstractModule() {
  override fun configure() {
    bind<WebConfig>().toInstance(config)
    bind<ActionExceptionLogLevelConfig>().toInstance(config.action_exception_log_level)

    install(ServiceModule(key = JettyService::class.toKey(), dependsOn = jettyDependsOn))
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
    multibind<Unmarshaller.Factory>().to<JsonUnmarshaller.Factory>()
    multibind<Unmarshaller.Factory>().to<ProtobufUnmarshaller.Factory>()
    multibind<Unmarshaller.Factory>().to<MultipartUnmarshaller.Factory>()

    // Initialize empty sets for our multibindings.
    newMultibinder<NetworkInterceptor.Factory>()
    newMultibinder<ApplicationInterceptor.Factory>()
    newMultibinder<StaticResourceEntry>()
    newMultibinder<WebProxyEntry>()
    val logContextProviderBinder = MapBinder.newMapBinder(
      binder(),
      String::class.java, LogContextProvider::class.java
    )
    logContextProviderBinder.addBinding(RequestLogContextInterceptor.MDC_HTTP_METHOD)
      .to<RequestHttpMethodLogContextProvider>()
    logContextProviderBinder.addBinding(RequestLogContextInterceptor.MDC_PROTOCOL)
      .to<RequestProtocolLogContextProvider>()
    logContextProviderBinder.addBinding(RequestLogContextInterceptor.MDC_REMOTE_ADDR)
      .to<RequestRemoteAddressLogContextProvider>()
    logContextProviderBinder.addBinding(RequestLogContextInterceptor.MDC_REQUEST_URI)
      .to<RequestURILogContextProvider>()

    // Register built-in interceptors. Interceptors run in the order in which they are
    // installed, and the order of these interceptors is critical.

    newMultibinder<NetworkInterceptor.Factory>(BeforeContentEncoding::class)

    // Inflates a gzip compressed request
    multibind<NetworkInterceptor.Factory>(ForContentEncoding::class)
      .to<GunzipRequestBodyInterceptor.Factory>()

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

    newMultibinder<ConcurrencyLimiterFactory>()
    if (!config.concurrency_limiter_disabled) {
      // Shed calls when we're degraded.
      multibind<NetworkInterceptor.Factory>(MiskDefault::class)
        .to<ConcurrencyLimitsInterceptor.Factory>()
    }

    // Traces requests as they work their way through the system.
    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
      .to<TracingInterceptor.Factory>()

    // Convert and log application level exceptions into their appropriate response format
    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
      .to<ExceptionHandlingInterceptor.Factory>()

    // Optionally log request and response details
    multibind<NetworkInterceptor.Factory>(MiskDefault::class)
      .to<RequestLoggingInterceptor.Factory>()

    // Optionally log request and response body
    multibind<ApplicationInterceptor.Factory>(MiskDefault::class)
      .to<RequestBodyLoggingInterceptor.Factory>()

    newMultibinder<WebActionSeedDataTransformerFactory>()

    install(ExceptionMapperModule.create<WebActionException, WebActionExceptionMapper>())
    install(ExceptionMapperModule.create<IOException, IOExceptionMapper>())
    install(ExceptionMapperModule.create<EofException, EofExceptionMapper>())
    install(ExceptionMapperModule.create<RequestBodyException, RequestBodyExceptionMapper>())

    // Register built-in feature bindings.
    multibind<FeatureBinding.Factory>().toInstance(PathParamFeatureBinding.Factory)
    multibind<FeatureBinding.Factory>().toInstance(QueryParamFeatureBinding.Factory)
    multibind<FeatureBinding.Factory>().toInstance(FormValueFeatureBinding.Factory)
    multibind<FeatureBinding.Factory>().toInstance(RequestHeadersFeatureBinding.Factory)
    multibind<FeatureBinding.Factory>().toInstance(WebSocketFeatureBinding.Factory)
    multibind<FeatureBinding.Factory>().toInstance(WebSocketListenerFeatureBinding.Factory)
    multibind<FeatureBinding.Factory>().to<RequestBodyFeatureBinding.Factory>()
    multibind<FeatureBinding.Factory>().to<ResponseBodyFeatureBinding.Factory>()
    multibind<FeatureBinding.Factory>().to<GrpcFeatureBinding.Factory>()

    // Install infrastructure support
    install(CertificatesModule())

    // Bind build-in actions.
    install(WebActionModule.create<StatusAction>())
    install(WebActionModule.create<ReadinessCheckAction>())
    install(WebActionModule.create<LivenessCheckAction>())
    install(WebActionModule.create<NotFoundAction>())

    val maxThreads = config.jetty_max_thread_pool_size
    val minThreads = Math.min(config.jetty_min_thread_pool_size, maxThreads)
    val idleTimeout = 60_000
    if (config.jetty_max_thread_pool_queue_size > 0) {
      val threadPool = QueuedThreadPool(
        maxThreads,
        minThreads,
        idleTimeout,
        provideThreadPoolQueue(getProvider(ThreadPoolQueueMetrics::class.java))
      )
      threadPool.name = "jetty-thread"
      bind<ThreadPool>().toInstance(threadPool)
      bind<MeasuredThreadPool>().toInstance(MeasuredQueuedThreadPool(threadPool))
    } else {
      val executor = ThreadPoolExecutor(
        minThreads,
        maxThreads,
        idleTimeout.toLong(),
        TimeUnit.MILLISECONDS,
        SynchronousQueue()
      )
      val threadPool = ExecutorThreadPool(executor)
      threadPool.name = "jetty-thread"
      bind<ThreadPool>().toInstance(threadPool)
      bind<MeasuredThreadPool>().toInstance(MeasuredThreadPoolExecutor(executor))
    }
  }

  @Provides @Singleton
  fun provideStatisticsHandler(): StatisticsHandler {
    return StatisticsHandler()
  }

  @Provides @Singleton
  fun provideGzipHandler(): GzipHandler {
    return GzipHandler()
  }

  private fun provideThreadPoolQueue(metrics: Provider<ThreadPoolQueueMetrics>):
    BlockingQueue<Runnable> {
      return if (config.enable_thread_pool_queue_metrics) {
        TimedBlockingQueue(
          config.jetty_max_thread_pool_queue_size
        ) { d -> metrics.get().recordQueueLatency(d) }
      } else {
        ArrayBlockingQueue<Runnable>(config.jetty_max_thread_pool_queue_size)
      }
    }

  class MiskCallerProvider @Inject constructor(
    private val authenticators: List<MiskCallerAuthenticator>
  ) : ActionScopedProvider<MiskCaller?> {
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
