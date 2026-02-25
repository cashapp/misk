package misk.web.interceptors

import com.google.inject.Inject
import io.opentracing.Span
import io.opentracing.SpanContext
import io.opentracing.Tracer
import io.opentracing.propagation.Format
import io.opentracing.tag.Tags
import io.opentracing.tag.Tags.SPAN_KIND_SERVER
import jakarta.inject.Singleton
import misk.Action
import misk.logging.getLogger
import misk.tracing.interceptors.TextMultimapExtractAdapter
import misk.web.DispatchMechanism
import misk.web.NetworkChain
import misk.web.NetworkInterceptor

private val logger = getLogger<GrpcRootSpanInterceptor>()

/**
 * Creates a root `servlet.request` span for gRPC requests when dd-trace-java fails to do so.
 *
 * dd-trace-java's Jetty instrumentation doesn't create root spans for HTTP/2 requests over
 * Unix Domain Sockets (JEP 380). Without a root span, TracingInterceptor's `http.action` spans
 * become orphans that get dropped. This interceptor fills that gap by creating a root span only
 * when no active span exists.
 *
 * Registered with [BeforeContentEncoding] so it runs before TracingInterceptor in the chain.
 * It is a no-op when dd-trace-java works correctly (i.e. an active span already exists) or for
 * non-gRPC requests.
 */
internal class GrpcRootSpanInterceptor internal constructor(private val tracer: Tracer) : NetworkInterceptor {
  @Singleton
  class Factory @Inject constructor() : NetworkInterceptor.Factory {
    @Inject(optional = true) var tracer: Tracer? = null

    override fun create(action: Action): NetworkInterceptor? {
      val tracer = tracer ?: return null
      // Only install for gRPC actions.
      if (action.dispatchMechanism != DispatchMechanism.GRPC) return null
      return GrpcRootSpanInterceptor(tracer)
    }
  }

  override fun intercept(chain: NetworkChain) {
    // If dd-trace-java already created a root span, do nothing.
    if (tracer.activeSpan() != null) {
      chain.proceed(chain.httpCall)
      return
    }

    val spanBuilder =
      tracer
        .buildSpan("servlet.request")
        .withTag(Tags.SPAN_KIND.key, SPAN_KIND_SERVER)
        .withTag(Tags.HTTP_METHOD.key, chain.httpCall.dispatchMechanism.method)
        .withTag(Tags.HTTP_URL.key, chain.httpCall.url.toString())
        .withTag(Tags.COMPONENT.key, "jetty")

    val parentContext: SpanContext? =
      try {
        tracer.extract(
          Format.Builtin.HTTP_HEADERS,
          TextMultimapExtractAdapter(chain.httpCall.requestHeaders.toMultimap()),
        )
      } catch (e: Exception) {
        logger.warn(
          "Failure attempting to extract span context for gRPC root span. " +
            "Existing context, if any, will be ignored",
          e,
        )
        null
      }

    if (parentContext != null) {
      spanBuilder.asChildOf(parentContext)
    }

    val span: Span = spanBuilder.start()
    val scope = tracer.scopeManager().activate(span)
    span.setTag("resource.name", "${chain.httpCall.dispatchMechanism.method} ${chain.httpCall.url}")
    try {
      chain.proceed(chain.httpCall)
      Tags.HTTP_STATUS.set(span, chain.httpCall.statusCode)
      if (chain.httpCall.statusCode >= 500) {
        Tags.ERROR.set(span, true)
      }
    } catch (t: Throwable) {
      Tags.ERROR.set(span, true)
      throw t
    } finally {
      scope.close()
      span.finish()
    }
  }
}
