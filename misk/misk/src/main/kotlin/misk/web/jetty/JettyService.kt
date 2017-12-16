package misk.web.jetty

import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.AbstractIdleService
import misk.logging.getLogger
import misk.web.WebConfig
import okhttp3.HttpUrl
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.servlet.ServletHolder
import javax.inject.Inject
import javax.inject.Singleton

private val logger = getLogger<JettyService>()

@Singleton
class JettyService @Inject internal constructor(
    private val webActionsServlet: WebActionsServlet,
    private val webConfig: WebConfig
) : AbstractIdleService() {

  private val server = Server()

  val serverUrl: HttpUrl
    get() {
      return HttpUrl.Builder()
          .scheme(server.uri.scheme)
          .host(server.uri.host)
          .port(server.uri.port)
          .build()
    }

  override fun startUp() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Starting up Jetty")

    val serverConnector = ServerConnector(server)
    serverConnector.port = webConfig.port
    serverConnector.idleTimeout = webConfig.idle_timeout
    val servletHandler = ServletHandler()
    servletHandler.addServletWithMapping(ServletHolder(webActionsServlet), "/*")

    server.addConnector(serverConnector)
    server.addManaged(servletHandler)

    server.handler = servletHandler

    server.start()

    logger.info { "Started Jetty in $stopwatch on port ${webConfig.port}" }
  }

  override fun shutDown() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Stopping Jetty")

    server.stop()

    logger.info { "Stopped Jetty in $stopwatch" }
  }
}
