package misk.web.jetty

import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.ThreadFactoryBuilder
import misk.concurrent.ExecutorServiceFactory
import misk.logging.getLogger
import misk.security.ssl.CipherSuites
import misk.security.ssl.SslLoader
import misk.security.ssl.TlsProtocols
import misk.web.WebConfig
import misk.web.WebSslConfig
import misk.web.mediatype.MediaTypes
import okhttp3.HttpUrl
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory
import org.eclipse.jetty.server.ConnectionFactory
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.NetworkConnector
import org.eclipse.jetty.server.SecureRequestCustomizer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnectionStatistics
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.SslConnectionFactory
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.server.handler.StatisticsHandler
import org.eclipse.jetty.server.handler.gzip.GzipHandler
import org.eclipse.jetty.servlet.FilterHolder
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.servlets.CrossOriginFilter
import org.eclipse.jetty.unixsocket.UnixSocketConnector
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.util.thread.ThreadPool
import java.net.InetAddress
import java.util.EnumSet
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.servlet.DispatcherType
import javax.servlet.FilterConfig

private val logger = getLogger<JettyService>()

@Singleton
class JettyService @Inject internal constructor(
  private val sslLoader: SslLoader,
  private val webActionsServlet: WebActionsServlet,
  private val webConfig: WebConfig,
  threadPool: ThreadPool,
  private val connectionMetricsCollector: JettyConnectionMetricsCollector,
  private val statisticsHandler: StatisticsHandler,
  private val gzipHandler: GzipHandler
) : AbstractIdleService() {
  private val server = Server(threadPool)
  val healthServerUrl: HttpUrl? get() = server.healthUrl
  val httpServerUrl: HttpUrl get() = server.httpUrl!!
  val httpsServerUrl: HttpUrl? get() = server.httpsUrl

  override fun startUp() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Starting Jetty")

    if (webConfig.health_port >= 0) {
      val healthExecutor = ThreadPoolExecutor(
        // 2 threads for jetty acceptor and selector. 2 threads for k8s liveness/readiness.
        4,
        // Jetty can be flaky about rejecting near full capacity, so allow some growth.
        8,
        60L, TimeUnit.SECONDS,
        SynchronousQueue(),
        ThreadFactoryBuilder()
          .setNameFormat("jetty-health-%d")
          .build()
      )
      val healthConnector = ServerConnector(
        server,
        healthExecutor,
        null, /* scheduler */
        null /* buffer pool */,
        1,
        1,
        HttpConnectionFactory()
      )
      healthConnector.port = webConfig.health_port
      healthConnector.name = "health"
      server.addConnector(healthConnector)
    }

    val httpConnectionFactories = mutableListOf<ConnectionFactory>()
    val httpConfig = HttpConfiguration()
    httpConfig.customizeForGrpc()
    httpConfig.sendServerVersion = false
    if (webConfig.ssl != null) {
      httpConfig.securePort = webConfig.ssl.port
    }
    httpConnectionFactories += HttpConnectionFactory(httpConfig)
    if (webConfig.http2) {
      val http2 = HTTP2ServerConnectionFactory(httpConfig)
      if (webConfig.jetty_max_concurrent_streams != null) {
        http2.maxConcurrentStreams = webConfig.jetty_max_concurrent_streams
      }
      httpConnectionFactories += HTTP2CServerConnectionFactory(httpConfig)
    }

    // TODO(mmihic): Allow require running only on HTTPS?
    val httpConnector = ServerConnector(
      server,
      null /* executor */,
      null /* scheduler */,
      null /* buffer pool */,
      webConfig.acceptors ?: -1,
      webConfig.selectors ?: -1,
      httpConnectionFactories.toTypedArray()
    )
    httpConnector.port = webConfig.port
    httpConnector.idleTimeout = webConfig.idle_timeout
    httpConnector.reuseAddress = true
    httpConnector.name = "http"
    if (webConfig.queue_size != null) {
      httpConnector.acceptQueueSize = webConfig.queue_size
    }

    webConfig.host?.let { httpConnector.host = it }
    httpConnector.addBean(
      connectionMetricsCollector.newConnectionListener(
        "http",
        webConfig.port
      )
    )
    server.addConnector(httpConnector)

    if (webConfig.ssl != null) {
      val sslContextFactory = SslContextFactory.Server()
      sslContextFactory.keyStore = sslLoader.loadCertStore(webConfig.ssl.cert_store)!!.keyStore
      sslContextFactory.setKeyStorePassword(webConfig.ssl.cert_store.passphrase)
      webConfig.ssl.trust_store?.let {
        sslContextFactory.trustStore = sslLoader.loadTrustStore(it)!!.keyStore
      }
      when (webConfig.ssl.mutual_auth) {
        WebSslConfig.MutualAuth.REQUIRED -> sslContextFactory.needClientAuth = true
        WebSslConfig.MutualAuth.DESIRED -> sslContextFactory.wantClientAuth = true
        WebSslConfig.MutualAuth.NONE -> {
          // Neither needed nor wanted
        }
      }

      val httpsConnectionFactories = mutableListOf<ConnectionFactory>()

      // By default, Jetty excludes a number of common cipher suites. This default set is too
      // restrictive. Clear the set of excluded suites and define the suites to include below.
      sslContextFactory.setExcludeCipherSuites()
      sslContextFactory.setIncludeProtocols(*TlsProtocols.safe)
      sslContextFactory.setIncludeCipherSuites(*CipherSuites.safe)

      val httpsConfig = HttpConfiguration(httpConfig)
      httpsConfig.addCustomizer(SecureRequestCustomizer())

      val ssl = SslConnectionFactory(sslContextFactory, "alpn")
      httpsConnectionFactories += ssl

      val alpnProtocols = if (webConfig.http2) listOf("h2", "http/1.1") else listOf("http/1.1")
      val alpn = ALPNServerConnectionFactory(*alpnProtocols.toTypedArray())
      alpn.defaultProtocol = "http/1.1"
      httpsConnectionFactories += alpn

      if (webConfig.http2) {
        val http2 = HTTP2ServerConnectionFactory(httpsConfig)
        if (webConfig.jetty_max_concurrent_streams != null) {
          http2.maxConcurrentStreams = webConfig.jetty_max_concurrent_streams
        }
        httpsConnectionFactories += http2
      }

      val http1 = HttpConnectionFactory(httpsConfig)
      httpsConnectionFactories += http1

      val httpsConnector = ServerConnector(
        server,
        null /* executor */,
        null /* scheduler */,
        null /* buffer pool */,
        webConfig.acceptors ?: -1,
        webConfig.selectors ?: -1,
        httpsConnectionFactories.toTypedArray()
      )
      httpsConnector.port = webConfig.ssl.port
      httpsConnector.idleTimeout = webConfig.idle_timeout
      httpsConnector.reuseAddress = true
      if (webConfig.queue_size != null) {
        httpsConnector.acceptQueueSize = webConfig.queue_size
      }
      webConfig.host?.let { httpsConnector.host = it }
      httpsConnector.addBean(
        connectionMetricsCollector.newConnectionListener(
          "https",
          webConfig.ssl.port
        )
      )
      httpsConnector.name = "https"
      server.addConnector(httpsConnector)
    }

    if (webConfig.unix_domain_socket != null) {
      val udsConnFactories = mutableListOf<ConnectionFactory>()
      udsConnFactories.add(HttpConnectionFactory(httpConfig))
      if (webConfig.unix_domain_socket.h2c == true) {
        udsConnFactories.add(HTTP2CServerConnectionFactory(httpConfig))
      }

      val udsConnector = UnixSocketConnector(
        server,
        null /* executor */,
        null /* scheduler */,
        null /* buffer pool */,
        webConfig.selectors ?: -1,
        udsConnFactories.toTypedArray()
      )
      udsConnector.setUnixSocket(webConfig.unix_domain_socket.path)
      udsConnector.addBean(connectionMetricsCollector.newConnectionListener("http", 0))
      udsConnector.name = "uds"
      server.addConnector(udsConnector)
    }

    // TODO(mmihic): Force security handler?
    val servletContextHandler = ServletContextHandler()
    servletContextHandler.addServlet(ServletHolder(webActionsServlet), "/*")
    server.addManaged(servletContextHandler)

    statisticsHandler.handler = servletContextHandler
    statisticsHandler.server = server

    server.stopAtShutdown = true
    // Kubernetes sends a SIG_TERM and gives us 30 seconds to stop gracefully.
    server.stopTimeout = 25_000
    ServerConnectionStatistics.addToAllConnectors(server)

    gzipHandler.server = server
    if (webConfig.gzip) {
      gzipHandler.minGzipSize = webConfig.minGzipSize
      gzipHandler.addIncludedMethods("POST")
      gzipHandler.addExcludedMimeTypes(MediaTypes.APPLICATION_GRPC)
    } else {
      // GET is enabled by default for gzipHandler.
      gzipHandler.addExcludedMethods("GET")
    }
    gzipHandler.inflateBufferSize = 8192
    servletContextHandler.gzipHandler = gzipHandler

    server.handler = statisticsHandler

    webConfig.cors.forEach { (path, corsConfig) ->
      val holder = FilterHolder(CrossOriginFilter::class.java)
      holder.setInitParameter(
        CrossOriginFilter.ALLOWED_ORIGINS_PARAM,
        corsConfig.allowedOrigins.joinToString(",")
      )
      holder.setInitParameter(
        CrossOriginFilter.ALLOWED_METHODS_PARAM,
        corsConfig.allowedMethods.joinToString(",")
      )
      holder.setInitParameter(
        CrossOriginFilter.ALLOWED_HEADERS_PARAM,
        corsConfig.allowedHeaders.joinToString(",")
      )
      holder.setInitParameter(
        CrossOriginFilter.ALLOW_CREDENTIALS_PARAM,
        corsConfig.allowCredentials.toString()
      )
      holder.setInitParameter(
        CrossOriginFilter.PREFLIGHT_MAX_AGE_PARAM,
        corsConfig.preflightMaxAge
      )
      holder.setInitParameter(
        CrossOriginFilter.CHAIN_PREFLIGHT_PARAM,
        corsConfig.chainPreflight.toString()
      )
      holder.setInitParameter(
        CrossOriginFilter.EXPOSED_HEADERS_PARAM,
        corsConfig.exposedHeaders.joinToString(",")
      )
      servletContextHandler.addFilter(holder, path, EnumSet.of(DispatcherType.REQUEST))
    }

    server.start()

    logger.info {
      if (webConfig.ssl != null) {
        "Started Jetty in $stopwatch on port ${webConfig.port}/${webConfig.ssl.port}"
      } else {
        "Started Jetty in $stopwatch on port ${webConfig.port}"
      }
    }
  }

  override fun shutDown() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Stopping Jetty")

    if (server.isRunning) {
      server.stop()
    }

    logger.info { "Stopped Jetty in $stopwatch" }
  }
}

private val Server.healthUrl: HttpUrl?
  get() {
    return connectors
      .mapNotNull { it as? NetworkConnector }
      .firstOrNull { it.name == "health" }
      ?.toHttpUrl()
  }

private val Server.httpUrl: HttpUrl?
  get() {
    return connectors
      .mapNotNull { it as? NetworkConnector }
      .firstOrNull { it.name == "http" }
      ?.toHttpUrl()
  }

private val Server.httpsUrl: HttpUrl?
  get() {
    return connectors
      .mapNotNull { it as? NetworkConnector }
      .firstOrNull { it.name == "https" }
      ?.toHttpUrl()
  }

private fun NetworkConnector.toHttpUrl(): HttpUrl {
  val context = server.getChildHandlerByClass(ContextHandler::class.java)
  val protocol = defaultConnectionFactory.protocol
  val scheme = if (protocol.startsWith("SSL-") || protocol == "SSL") "https" else "http"

  val virtualHosts = context?.virtualHosts ?: arrayOf<String>()
  val explicitHost = if (virtualHosts.isEmpty()) host else virtualHosts[0]

  return HttpUrl.Builder()
    .scheme(scheme)
    .host(explicitHost ?: InetAddress.getLocalHost().hostAddress)
    .port(localPort)
    .build()
}

/**
 * Configures this config so that it can carry gRPC calls. In particular, gRPC needs to write to
 * the response stream before the request stream is completed. It also wants to send HTTP trailers.
 */
private fun HttpConfiguration.customizeForGrpc() {
  isDelayDispatchUntilContent = false
}