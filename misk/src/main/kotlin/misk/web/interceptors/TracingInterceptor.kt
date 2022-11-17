package misk.web.interceptors

import com.google.inject.Inject
import com.google.inject.Singleton
import io.opentracing.Span
import io.opentracing.SpanContext
import io.opentracing.Tracer
import io.opentracing.propagation.Format
import io.opentracing.tag.Tags
import io.opentracing.tag.Tags.SPAN_KIND_SERVER
import misk.Action
import misk.tracing.interceptors.TextMultimapExtractAdapter
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import wisp.logging.getLogger

private val logger = getLogger<TracingInterceptor>()

/**
 * Enables distributed tracing on all web actions, if a client has installed a tracer.
 */
internal class TracingInterceptor internal constructor(private val tracer: Tracer) :
  NetworkInterceptor {
  @Singleton
  class Factory @Inject constructor() : NetworkInterceptor.Factory {
    @Inject(optional = true) var tracer: Tracer? = null

    // NOTE(nb): returning null ensures interceptor is filtered out when generating interceptors to
    // apply for a specific action. See WebActionModule for implementation details
    override fun create(action: Action) = tracer?.let { TracingInterceptor(it) }
  }

  override fun intercept(chain: NetworkChain) {
    val spanBuilder = tracer.buildSpan("http.action")
      .withTag(Tags.HTTP_METHOD.key, chain.httpCall.dispatchMechanism.method)
      .withTag(Tags.HTTP_URL.key, chain.httpCall.url.toString())
      .withTag(Tags.SPAN_KIND.key, SPAN_KIND_SERVER)

    val parentSpan: Span? = tracer.activeSpan()
    if (parentSpan != null) {
      // Certain tracing implementations (Datadog) do their own header extraction. Skip our custom
      // one if that happened.
      spanBuilder.asChildOf(parentSpan)
    } else {
      val parentContext: SpanContext? = try {
        tracer.extract(
          Format.Builtin.HTTP_HEADERS,
          TextMultimapExtractAdapter(chain.httpCall.requestHeaders.toMultimap())
        )
      } catch (e: Exception) {
        logger.warn(
          "Failure attempting to extract span context. Existing context, if any," +
            " will be ignored in creation of span",
          e
        )
        null
      }

      if (parentContext != null) {
        spanBuilder.asChildOf(parentContext)
      }
    }

    val span = spanBuilder.start()
    val scope = tracer.scopeManager().activate(span)
    // This is a datadog convention. Must be set after span is created because otherwise it would
    // be overwritten by the method/url
    span.setTag("resource.name", chain.webAction.javaClass.name)
    try {
      chain.proceed(chain.httpCall)
      Tags.HTTP_STATUS.set(span, chain.httpCall.statusCode)
      if (chain.httpCall.statusCode > 399) {
        Tags.ERROR.set(span, true)
        // In case of gRPC errors, [HttpCall.networkStatusCode] is always 200, in which case the
        // parent (likely the root) span won't be able to know about the error.
        if (parentSpan != null) {
          Tags.ERROR.set(parentSpan, true)
        }
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
