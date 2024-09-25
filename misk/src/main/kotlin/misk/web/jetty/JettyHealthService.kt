package misk.web.jetty

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.ThreadFactoryBuilder
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.annotation.ExperimentalMiskApi
import misk.web.WebConfig
import mu.KLogger
import okhttp3.HttpUrl
import org.eclipse.jetty.http.UriCompliance
import org.eclipse.jetty.io.ConnectionStatistics
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.NetworkConnector
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.StatisticsHandler
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.thread.ExecutorThreadPool
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer
import wisp.logging.getLogger
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds

/**
 * The JettyHealthService is a standalone Jetty Instance for managing health checks in Misk.
 * It is unique in that it needs to start first to begin responding to health probes, but stop
 * last to continue responding until the entire service is shutdown.
 *
 * As this is not easily modelled in the service dependency graph, JettyHealthService is
 * managed externally to the graph by starting asynchronously before other services begin starting
 * and terminating after other services have gracefully shut down.
 */
@Singleton
internal class JettyHealthService @Inject internal constructor(
  private val webActionsServlet: WebActionsServlet,
  private val webConfig: WebConfig,
  private val connectionMetricsCollector: JettyConnectionMetricsCollector,
  @misk.web.JettyHealthService private val statisticsHandler: StatisticsHandler,
) : AbstractIdleService() {

  private val enabled by lazy {
    webConfig.jettyHealthServiceEnabled(logger)
  }

  private val server by lazy {
    val threadPool = ExecutorThreadPool(
      ThreadPoolExecutor(
        /* corePoolSize = */ 4,
        /* maximumPoolSize = */ 8,
        /* keepAliveTime = */ 60,
        /* unit = */ TimeUnit.MILLISECONDS,
        /* workQueue = */ SynchronousQueue(),
        /* threadFactory = */ ThreadFactoryBuilder()
        .setNameFormat("jetty-health-%d")
        .build()
      )
    ).apply {
      name = "jetty-health"
    }

    Server(threadPool)
  }

  internal val healthServerUrl: HttpUrl? by lazy {
    if (!enabled) {
      null
    } else {
      server
        .connectors
        .mapNotNull { it as? NetworkConnector }
        .first { it.name == "health" }
        .toHttpUrl()
    }
  }

  override fun startUp() {
    if (!enabled) {
      return
    }

    measureTimeMillis {
      logger.info("Starting Jetty Health Service")

      setupHttpConnector()
      setupServletHandler()
      setupServer()

      server.start()
    }.also {
      logger.info("Started Jetty Health in ${it.milliseconds} on port ${webConfig.health_port}")
    }
  }

  private fun setupHttpConnector() {
    val httpConnectionFactory = HttpConnectionFactory(
      HttpConfiguration().apply {
        uriCompliance = UriCompliance.RFC3986
        sendServerVersion = false
        setFormEncodedMethods()
      })

    server.addConnector(
      ServerConnector(
        /* server = */ server,
        /* executor = */ null,
        /* scheduler = */ null,
        /* bufferPool = */ null,
        /* acceptors = */ 1,
        /* selectors = */ 1,
        /* ...factories = */ httpConnectionFactory,
      ).apply {
        port = webConfig.health_port
        idleTimeout = webConfig.idle_timeout
        webConfig.override_shutdown_idle_timeout?.let {
          shutdownIdleTimeout = it
        }
        reuseAddress = true
        name = "health"
        webConfig.host?.let {
          host = it
        }
        addBean(
          connectionMetricsCollector.newConnectionListener(
            protocol = "http",
            port = webConfig.health_port
          )
        )
      }
    )
  }

  private fun setupServletHandler() {
    val servletContextHandler = ServletContextHandler().apply {
      addServlet(ServletHolder(webActionsServlet), "/_readiness")
      addServlet(ServletHolder(webActionsServlet), "/_liveness")
      addServlet(ServletHolder(webActionsServlet), "/_status")
    }

    JettyWebSocketServletContainerInitializer.configure(servletContextHandler, null)
    server.addManaged(servletContextHandler)
    statisticsHandler.handler = servletContextHandler
  }

  private fun setupServer() {
    // Kubernetes sends a SIG_TERM and gives us 30 seconds to stop gracefully.
    server.stopTimeout = 25_000

    statisticsHandler.server = server
    server.handler = statisticsHandler
    server.addBean(ConnectionStatistics())
  }

  override fun shutDown() {
    if (!enabled || !server.isRunning) {
      return
    }

    measureTimeMillis {
      server.stop()
    }.also {
      logger.info { "Stopped Jetty Health in ${it.milliseconds}" }
    }
  }

  companion object {
    private val logger = getLogger<JettyHealthService>()

    @OptIn(ExperimentalMiskApi::class)
    internal fun WebConfig.jettyHealthServiceEnabled(logger: KLogger? = null): Boolean =
      when {
        !health_dedicated_jetty_instance -> false
        health_port < 0 -> {
          logger?.info("Not Starting Jetty Health Service: health_port $health_port is <0 ")
          false
        }

        else -> true
      }
  }
}
