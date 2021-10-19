package misk.metrics.backends.prometheus

import com.google.common.util.concurrent.AbstractIdleService
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.HTTPServer
import wisp.logging.getLogger
import java.io.IOException
import java.net.InetSocketAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrometheusHttpService @Inject internal constructor(
  private val config: PrometheusConfig,
  private val registry: CollectorRegistry
) : AbstractIdleService() {
  private var httpServer: HTTPServer? = null

  override fun startUp() {
    log.info { "exposing prometheus metrics on port ${config.http_port}" }

    val socketAddr = if (config.hostname == null) InetSocketAddress(config.http_port)
    else InetSocketAddress(config.hostname, config.http_port)
    try {
      httpServer = HTTPServer(socketAddr, registry)
    } catch (e: IOException) {
      throw IOException("failed to expose prometheus metrics on port ${config.http_port}", e)
    }
  }

  override fun shutDown() {
    log.info { "shutting down prometheus metrics endpoint" }
    httpServer?.stop()
  }

  private companion object {
    val log = getLogger<PrometheusHttpService>()
  }
}
