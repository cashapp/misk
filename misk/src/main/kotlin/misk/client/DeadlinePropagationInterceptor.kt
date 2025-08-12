package misk.client

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.exceptions.GatewayTimeoutException
import misk.grpc.GrpcTimeoutMarshaller
import misk.scope.ActionScoped
import misk.web.RequestDeadlineMode
import misk.web.RequestDeadlinesConfig
import misk.web.WebConfig
import misk.web.interceptors.RequestDeadlineInterceptor
import misk.web.mediatype.MediaTypes
import misk.web.requestdeadlines.RequestDeadline
import misk.web.requestdeadlines.RequestDeadlineMetrics
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

internal class DeadlinePropagationInterceptor(
  private val clientAction: ClientAction,
  private val requestDeadlinesConfig: RequestDeadlinesConfig,
  private val requestDeadlineActionScope: ActionScoped<RequestDeadline>,
  private val metrics: RequestDeadlineMetrics,
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val requestDeadline = requestDeadlineActionScope.getIfInScope()

    // Handle case when no deadline is in scope
    if (requestDeadline?.remaining() == null) {
      return handleNoDeadlineInScope(chain)
    }

    // Always check deadline and emit metrics based on mode
    // At this point we know requestDeadline is not null (null case handled above)
    return when (requestDeadlinesConfig.mode) {
      RequestDeadlineMode.METRICS_ONLY -> handleDisabledMode(requestDeadline, chain)
      RequestDeadlineMode.PROPAGATE_ONLY -> handlePropagateOnlyMode(requestDeadline, chain)
      RequestDeadlineMode.ENFORCE_INBOUND -> handlePropagateOnlyMode(requestDeadline, chain)
      RequestDeadlineMode.ENFORCE_OUTBOUND, RequestDeadlineMode.ENFORCE_ALL -> handleEnforceMode(requestDeadline, chain)
    }
  }

  private fun handleNoDeadlineInScope(chain: Interceptor.Chain): Response {
    return when (requestDeadlinesConfig.mode) {
      RequestDeadlineMode.METRICS_ONLY -> {
        chain.proceed(chain.request())
      }
      else -> {
        // No RequestDeadline found in ActionScope, so propagate client.readTimeoutMillis as deadline
        val fallbackDeadlineMs: Long = chain.readTimeoutMillis().toLong()
        metrics.recordOutboundDeadlinePropagated(clientAction, fallbackDeadlineMs, chain.request())
        val newRequestBuilder = setRequestDeadlineHeadersOnOutbound(chain.request(), fallbackDeadlineMs)
        chain.proceed(newRequestBuilder.build())
      }
    }
  }

  private fun handleDisabledMode(requestDeadline: RequestDeadline, chain: Interceptor.Chain): Response {
    // Emit metrics, but do not propagate deadline headers or enforce
    if (requestDeadline.expired()) {
      metrics.recordOutboundDeadlineExceeded(
        clientAction, 
        enforced = false, 
        chain.request(),
        requestDeadline.expiredDuration().toMillis()
      )
    }
    return chain.proceed(chain.request())
  }

  private fun handlePropagateOnlyMode(requestDeadline: RequestDeadline, chain: Interceptor.Chain): Response {
    // Always emit metrics, propagate deadline headers, but do not enforce
    if (requestDeadline.expired()) {
      metrics.recordOutboundDeadlineExceeded(
        clientAction,
        enforced = false,
        chain.request(),
        requestDeadline.expiredDuration().toMillis()
      )
      // Deadline has expired, but config mode specifies it cannot be enforced. For this special case, omit deadline
      // headers, otherwise it will be 0 or a negative number. Let the downstream fallback to a default in its server
      // interceptor.
      return chain.proceed(chain.request())
    } else {
      val remainingMs = requestDeadline.remaining()!!.toMillis()
      metrics.recordOutboundDeadlinePropagated(clientAction, remainingMs, chain.request())
      val requestBuilder = setRequestDeadlineHeadersOnOutbound(chain.request(), remainingMs)
      return chain.proceed(requestBuilder.build())
    }
  }

  private fun handleEnforceMode(requestDeadline: RequestDeadline, chain: Interceptor.Chain): Response {
    if (requestDeadline.expired()) {
      metrics.recordOutboundDeadlineExceeded(
        clientAction,
        enforced = true,
        chain.request(),
        requestDeadline.expiredDuration().toMillis()
      )
      throw GatewayTimeoutException(
        "Deadline already expired, not initiating outbound call to ${chain.request().url}"
      )
    } else {
      val remainingMs = requestDeadline.remaining()!!.toMillis()
      metrics.recordOutboundDeadlinePropagated(clientAction, remainingMs, chain.request())
      val requestBuilder = setRequestDeadlineHeadersOnOutbound(chain.request(), remainingMs)
      return chain.proceed(requestBuilder.build())
    }
  }

  private fun setRequestDeadlineHeadersOnOutbound(request: Request, deadlineMs: Long): Request.Builder {
    val builder = request.newBuilder()

    // nb: Content-Type header not available yet at this point to distinguish http from grpc, so use "te"
    val isGrpcRequest = request.header("te")?.equals("trailers") == true

    if (isGrpcRequest) {
      // gRPC request - only add gRPC timeout header
      if (request.headers.get(GrpcTimeoutMarshaller.TIMEOUT_KEY).isNullOrEmpty()) {
        builder.header(
          GrpcTimeoutMarshaller.TIMEOUT_KEY,
          GrpcTimeoutMarshaller.toAsciiString(TimeUnit.MILLISECONDS.toNanos(deadlineMs)),
        )
      }
    } else {
      // HTTP request - only add HTTP deadline header
      builder.header(RequestDeadlineInterceptor.HTTP_HEADER_ENVOY_DEADLINE, deadlineMs.toString())
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
