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
import misk.logging.getLogger
import misk.tracing.interceptors.TextMultimapExtractAdapter
import misk.web.NetworkChain
import misk.web.NetworkInterceptor

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
    val scopeBuilder = tracer.buildSpan("http.action")
        .withTag(Tags.HTTP_METHOD.key, chain.httpCall.dispatchMechanism.method.toString())
        .withTag(Tags.HTTP_URL.key, chain.httpCall.url.toString())
        .withTag(Tags.SPAN_KIND.key, SPAN_KIND_SERVER)

    val parentSpan: Span? = tracer.activeSpan()
    if (parentSpan != null) {
      // Certain tracing implementations (Datadog) do their own header extraction. Skip our custom
      // one if that happened.
      scopeBuilder.asChildOf(parentSpan)
    } else {
      val parentContext: SpanContext? = try {
        tracer.extract(Format.Builtin.HTTP_HEADERS,
            TextMultimapExtractAdapter(chain.httpCall.requestHeaders.toMultimap()))
      } catch (e: Exception) {
        logger.warn("Failure attempting to extract span context. Existing context, if any," +
            " will be ignored in creation of span", e)
        null
      }

      if (parentContext != null) {
        scopeBuilder.asChildOf(parentContext)
      }
    }

    val scope = scopeBuilder.startActive(true)
    // This is a datadog convention. Must be set after span is created because otherwise it would
    // be overwritten by the method/url
    scope.span().setTag("resource.name", chain.webAction.javaClass.name)
    scope.use {
      try {
        chain.proceed(chain.httpCall)
        Tags.HTTP_STATUS.set(scope.span(), chain.httpCall.statusCode)
        if (chain.httpCall.statusCode > 399) {
          Tags.ERROR.set(scope.span(), true)
        }
      } catch (e: Exception) {
        Tags.ERROR.set(scope.span(), true)
        throw e
      }
    }
  }
}
