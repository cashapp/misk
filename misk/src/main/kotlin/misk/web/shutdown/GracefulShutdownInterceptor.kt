package misk.web.shutdown

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.Action
import misk.annotation.ExperimentalMiskApi
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.WebConfig
import misk.logging.getLogger

@Singleton
internal class GracefulShutdownInterceptorFactory @Inject constructor(
  private val webConfig: WebConfig,
  private val gracefulShutdownService: GracefulShutdownService
) : NetworkInterceptor.Factory {

  @OptIn(ExperimentalMiskApi::class)
  private val rejectionStatusCode = webConfig.graceful_shutdown_config!!.rejection_status_code

  private val interceptor = object : NetworkInterceptor {
    override fun intercept(chain: NetworkChain) {
      // Don't do anything for health checks.
      if (chain.httpCall.url.port == webConfig.health_port) {
        return chain.proceed(chain.httpCall)
      }

      // Reject new requests if configured to do so.
      if (gracefulShutdownService.shuttingDown && rejectionStatusCode > 0) {
        gracefulShutdownService.reportReject()
        logger.info {
          "Graceful Reject [code=$rejectionStatusCode][path=${chain.httpCall.url.encodedPath}]" +
            "[inFlight=${gracefulShutdownService.inFlightRequests}}]"
        }

        chain.httpCall.statusCode = rejectionStatusCode
        chain.httpCall.takeResponseBody()?.use { sink ->
          chain.httpCall.setResponseHeader("Content-Type", "application/json;charset=utf-8")
          chain.httpCall.setResponseHeader("Content-Length", "0")
          sink.writeUtf8("")
        }
        return
      }

      // Continue the chain, continuously tracking current in-flight requests.
      try {
        gracefulShutdownService.reportRequest()
        chain.proceed(chain.httpCall)
      } finally {
        gracefulShutdownService.reportRequestComplete()
      }
    }
  }

  override fun create(action: Action): NetworkInterceptor {
    return interceptor
  }

  companion object {
    private val logger = getLogger<GracefulShutdownInterceptorFactory>()
  }
}
