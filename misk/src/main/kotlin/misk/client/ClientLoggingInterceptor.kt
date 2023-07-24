package misk.client

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import wisp.logging.getLogger
import com.google.inject.Inject
import com.google.inject.Singleton

@Singleton internal class ClientLoggingInterceptor @Inject constructor(
  private val httpClientsConfig: HttpClientsConfig
): Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val result = chain.proceed(chain.request())

    if (httpClientsConfig.logRequests) {
      val outgoingRequest = result.request
      logger.info { "Outgoing request: ${outgoingRequest.url}, headers=${headers(outgoingRequest)}" }
    }

    return result
  }

  private fun headers(request: Request): Map<String, String> =
    request.headers
      .filter { (key, _) -> LOGGED_HEADERS.contains(key.lowercase()) }
      .toMap()

  companion object {
    val logger = getLogger<ClientLoggingInterceptor>()

    val LOGGED_HEADERS = listOf(
      "content-type",
      "user-agent",
      "content-length",
      // Also show tracing headers. These are also in logs, but showing them in the headers
      // gives us more confidence that traces were sent from service to service.
      "x-b3-traceid",
      "x-b3-spanid",
      "x-ddtrace-parent_trace_id",
      "x-ddtrace-parent_span_id",
      "x-datadog-parent-id",
      "x-datadog-trace-id",
      "x-datadog-span-id",
    )
  }
}
