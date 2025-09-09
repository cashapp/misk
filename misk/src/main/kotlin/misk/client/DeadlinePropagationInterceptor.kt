package misk.client

import com.squareup.wire.GrpcMethod
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.grpc.GrpcTimeoutMarshaller
import misk.scope.ActionScoped
import misk.web.RequestDeadlineMode
import misk.web.RequestDeadlinesConfig
import misk.web.WebConfig
import misk.web.interceptors.RequestDeadlineInterceptor
import misk.web.interceptors.RequestDeadlineInterceptor.Companion.CUSTOM_GRPC_TIMEOUT_PROPAGATE_HEADER
import misk.web.requestdeadlines.DeadlineExceededException
import misk.web.requestdeadlines.RequestDeadline
import misk.web.requestdeadlines.RequestDeadlineMetrics
import misk.web.requestdeadlines.RequestDeadlineMetrics.SourceLabel.OKHTTP_TIMEOUT
import misk.web.requestdeadlines.RequestDeadlineMetrics.SourceLabel.PROPAGATED_DEADLINE
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.time.Duration

internal class DeadlinePropagationInterceptor(
  private val clientAction: ClientAction,
  private val requestDeadlinesConfig: RequestDeadlinesConfig,
  private val requestDeadlineActionScope: ActionScoped<RequestDeadline>,
  private val metrics: RequestDeadlineMetrics,
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val requestDeadline = requestDeadlineActionScope.getIfInScope()
    val isGrpc = chain.request().tag(GrpcMethod::class.java) != null

    // Handle case when no deadline is in scope, like if requests were being made from executor or coroutine threads
    // where the ActionScope has not been explicitly passed along
    if (requestDeadline?.remaining() == null) {
      return handleNoDeadlineInScope(chain, isGrpc)
    }

    // Always check deadline and emit metrics based on mode
    // At this point we know requestDeadline is not null (null case handled above)
    return when (requestDeadlinesConfig.mode) {
      RequestDeadlineMode.METRICS_ONLY -> handleDisabledMode(requestDeadline, chain, isGrpc)
      RequestDeadlineMode.PROPAGATE_ONLY, RequestDeadlineMode.ENFORCE_INBOUND -> handlePropagateOnlyMode(requestDeadline, chain, isGrpc)
      RequestDeadlineMode.ENFORCE_OUTBOUND, RequestDeadlineMode.ENFORCE_ALL -> handleEnforceMode(requestDeadline, chain, isGrpc)
    }
  }

  private fun handleNoDeadlineInScope(chain: Interceptor.Chain, isGrpc: Boolean): Response {
    metrics.recordNoDeadlineInScope(clientAction, isGrpc)

    // For METRICS_ONLY mode or when no fallback deadline exists, proceed with original request
    if (requestDeadlinesConfig.mode == RequestDeadlineMode.METRICS_ONLY) {
      return chain.proceed(chain.request())
    }

    val okhttpClientFallbackDeadline = maybeOkHttpClientCallTimeout(chain)
      ?: return chain.proceed(chain.request())

    val enforced = requestDeadlinesConfig.mode in setOf(RequestDeadlineMode.ENFORCE_OUTBOUND, RequestDeadlineMode.ENFORCE_ALL)
    metrics.recordOutboundDeadlinePropagated(clientAction, okhttpClientFallbackDeadline, isGrpc, OKHTTP_TIMEOUT)
    val newRequestBuilder = setRequestDeadlineHeadersOnOutbound(chain.request(), okhttpClientFallbackDeadline, isGrpc, enforced)
    return chain.proceed(newRequestBuilder.build())
  }

  private fun handleDisabledMode(requestDeadline: RequestDeadline, chain: Interceptor.Chain, isGrpc: Boolean): Response {
    // Emit metrics, but do not propagate deadline headers or enforce
    if (requestDeadline.expired()) {
      metrics.recordOutboundDeadlineExceeded(
        clientAction,
        enforced = false,
        isGrpc,
        requestDeadline.expiredDuration())
    }
    return chain.proceed(chain.request())
  }

  private fun handlePropagateOnlyMode(requestDeadline: RequestDeadline, chain: Interceptor.Chain, isGrpc: Boolean): Response {
    // Always emit metrics, propagate deadline headers, but do not enforce
    val enforced = false
    if (requestDeadline.expired()) {
      metrics.recordOutboundDeadlineExceeded(
        clientAction,
        enforced,
        isGrpc,
        requestDeadline.expiredDuration()
      )
      // Deadline has expired, but config mode specifies it cannot be enforced. For this special case, omit deadline
      // headers, otherwise it will be 0 or a negative number. Let the downstream fallback to a default in its server
      // interceptor.
      return chain.proceed(chain.request())
    } else {
      val (deadline, source) = determineEffectiveDeadline(requestDeadline, chain)
      metrics.recordOutboundDeadlinePropagated(clientAction, deadline, isGrpc, source)
      val requestBuilder = setRequestDeadlineHeadersOnOutbound(chain.request(), deadline, isGrpc, enforced)
      return chain.proceed(requestBuilder.build())
    }
  }

  private fun handleEnforceMode(requestDeadline: RequestDeadline, chain: Interceptor.Chain, isGrpc: Boolean): Response {
    val enforced = true
    if (requestDeadline.expired()) {
      metrics.recordOutboundDeadlineExceeded(
        clientAction,
        enforced,
        isGrpc,
        requestDeadline.expiredDuration()
      )
      throw DeadlineExceededException(
        "Deadline already expired, not initiating outbound call to ${chain.request().url}"
      )
    } else {
      val (deadline, source) = determineEffectiveDeadline(requestDeadline, chain)
      metrics.recordOutboundDeadlinePropagated(clientAction, deadline, isGrpc, source)
      val requestBuilder = setRequestDeadlineHeadersOnOutbound(chain.request(), deadline, isGrpc, enforced)
      return chain.proceed(requestBuilder.build())
    }
  }

  /**
   * Determines the effective deadline by taking the minimum of the request deadline and OkHttp client timeout.
   * For OkHttpClient timeout, prefer callTimeout if it exists and > 0.
   * @return Pair of (deadline, source) where deadline is a Duration and source indicates which timeout was used
   */
  private fun determineEffectiveDeadline(requestDeadline: RequestDeadline, chain: Interceptor.Chain): Pair<Duration, String> {
    val propagatedDeadline = requestDeadline.remaining()!!
    val okHttpClientTimeout = maybeOkHttpClientCallTimeout(chain)

    return if (okHttpClientTimeout == null || propagatedDeadline <= okHttpClientTimeout) {
      propagatedDeadline to PROPAGATED_DEADLINE
    } else {
      okHttpClientTimeout to OKHTTP_TIMEOUT
    }
  }

  private fun maybeOkHttpClientCallTimeout(chain: Interceptor.Chain): Duration? {
    val callTimeoutNanos = chain.call().timeout().timeoutNanos()
    return Duration.ofNanos(callTimeoutNanos).takeIf { callTimeoutNanos != 0L }
  }

  private fun setRequestDeadlineHeadersOnOutbound(
    request: Request,
    deadline: Duration,
    isGrpc: Boolean,
    enforced: Boolean
  ): Request.Builder {
    val builder = request.newBuilder()
    if (isGrpc) {
      // gRPC request - use real header `grpc-timeout` for enforcing modes, shadow header for non-enforcing modes
      val grpcTimeoutHeader = if (enforced) GrpcTimeoutMarshaller.TIMEOUT_KEY else CUSTOM_GRPC_TIMEOUT_PROPAGATE_HEADER
      builder.header(
        grpcTimeoutHeader,
        GrpcTimeoutMarshaller.toAsciiString(deadline.toNanos()),
      )
    } else {
      // HTTP request - only add HTTP deadline header using ISO8601 duration format
      builder.header(RequestDeadlineInterceptor.HTTP_HEADER_X_REQUEST_DEADLINE, deadline.toString())
    }

    return builder
  }

  @Singleton
  class Factory
  @Inject
  constructor(
    private val requestDeadlineActionScope: ActionScoped<RequestDeadline>,
    private val webConfig: WebConfig,
    private val metrics: RequestDeadlineMetrics,
  ) : ClientApplicationInterceptorFactory {

    override fun create(action: ClientAction) =
      DeadlinePropagationInterceptor(action, webConfig.request_deadlines, requestDeadlineActionScope, metrics)
  }
}
