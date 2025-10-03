package misk.web.jetty

import com.google.common.base.Stopwatch
import com.google.common.base.Strings
import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.squareup.wire.internal.newMutableList
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.security.ssl.CipherSuites
import misk.security.ssl.SslLoader
import misk.security.ssl.TlsProtocols
import misk.web.WebConfig
import misk.web.WebSslConfig
import misk.web.WebUnixDomainSocketConfig
import misk.web.jetty.JettyHealthService.Companion.jettyHealthServiceEnabled
import misk.web.mediatype.MediaTypes
import okhttp3.HttpUrl
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory
import org.eclipse.jetty.http.UriCompliance
import org.eclipse.jetty.http2.server.AbstractHTTP2ServerConnectionFactory
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory
import org.eclipse.jetty.io.ConnectionStatistics
import org.eclipse.jetty.server.ConnectionFactory
import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.NetworkConnector
import org.eclipse.jetty.server.SecureRequestCustomizer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.SslConnectionFactory
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.server.handler.StatisticsHandler
import org.eclipse.jetty.server.handler.gzip.GzipHandler
import org.eclipse.jetty.servlet.FilterHolder
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.servlets.CrossOriginFilter
import org.eclipse.jetty.unixdomain.server.UnixDomainServerConnector
import org.eclipse.jetty.unixsocket.server.UnixSocketConnector
import org.eclipse.jetty.util.JavaVersion
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.util.thread.ThreadPool
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer
import misk.logging.getLogger
import java.io.File
import java.io.IOException
import java.lang.Thread.sleep
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.attribute.PosixFilePermissions
import java.util.EnumSet
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.servlet.DispatcherType

@Singleton
class JettyService @Inject internal constructor(
  private val sslLoader: SslLoader,
  private val webActionsServlet: WebActionsServlet,
  private val webConfig: WebConfig,
  threadPool: ThreadPool,
  private val connectionMetricsCollector: JettyConnectionMetricsCollector,
  private val statisticsHandler: StatisticsHandler,
  private val gzipHandler: GzipHandler,
  private val http2RateControlFactory: MeasuredWindowRateControl.Factory
) : AbstractIdleService() {
  private val server = Server(threadPool)
  val healthServerUrl: HttpUrl? get() = server.healthUrl
  val httpServerUrl: HttpUrl get() = server.httpUrl!!
  val httpsServerUrl: HttpUrl? get() = server.httpsUrl
  private var healthExecutor: ThreadPoolExecutor? = null

  override fun startUp() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Starting Jetty")

    if (!webConfig.jettyHealthServiceEnabled() && webConfig.health_port >= 0) {
      healthExecutor = ThreadPoolExecutor(
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
    httpConfig.uriCompliance = UriCompliance.RFC3986
    httpConfig.sendServerVersion = false
    if (webConfig.ssl != null) {
      httpConfig.securePort = webConfig.ssl.port
    }
    if (webConfig.http_request_header_size != null) {
      httpConfig.requestHeaderSize = webConfig.http_request_header_size
    }
    if (webConfig.http_header_cache_size != null) {
      httpConfig.headerCacheSize = webConfig.http_header_cache_size
    }
    if (webConfig.jetty_output_buffer_size != null) {
      httpConfig.outputBufferSize = webConfig.jetty_output_buffer_size
    }
    httpConfig.isUseInputDirectByteBuffers = webConfig.jetty_use_input_direct_byte_buffers
    httpConfig.isUseOutputDirectByteBuffers = webConfig.jetty_use_output_direct_byte_buffers
    httpConnectionFactories += HttpConnectionFactory(httpConfig)
    if (webConfig.http2) {
      val http2 = HTTP2CServerConnectionFactory(httpConfig)
      http2.customize(webConfig)
      http2.rateControlFactory = http2RateControlFactory
      httpConnectionFactories += http2
    }

    // TODO(mmihic): Allow require running only on HTTPS?
    val httpConnector = ServerConnector(
      server,
      null /* executor */,
      null /* scheduler */,
      null /* buffer pool */,
      webConfig.acceptors ?: -1,
      webConfig.selectors ?: -1,
      *httpConnectionFactories.toTypedArray()
    )
    httpConnector.port = webConfig.port
    httpConnector.idleTimeout = webConfig.idle_timeout
    if (webConfig.override_shutdown_idle_timeout != null) {
      httpConnector.shutdownIdleTimeout = webConfig.override_shutdown_idle_timeout
    }
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

      when (webConfig.ssl.cipher_compatibility) {
        WebSslConfig.CipherCompatibility.COMPATIBLE -> {
          // By default, Jetty excludes a number of common cipher suites. This default set is too
          // restrictive. Clear the set of excluded suites and define the suites to include below.
          sslContextFactory.setExcludeCipherSuites()
          sslContextFactory.setIncludeProtocols(*TlsProtocols.compatible)
          sslContextFactory.setIncludeCipherSuites(*CipherSuites.compatible)
        }

        WebSslConfig.CipherCompatibility.MODERN -> {
          // Use Jetty's default set of protocols and cipher suites.
        }

        WebSslConfig.CipherCompatibility.RESTRICTED -> {
          sslContextFactory.setIncludeProtocols(*TlsProtocols.restricted)
          // Use Jetty's default set of cipher suites for now; we can restrict it further later
          // if desired.
        }
      }

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
        http2.customize(webConfig)
        http2.rateControlFactory = http2RateControlFactory
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
        *httpsConnectionFactories.toTypedArray()
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

    val socketConfigs = newMutableList<WebUnixDomainSocketConfig>()
    if (webConfig.unix_domain_socket != null) {
      socketConfigs.add(webConfig.unix_domain_socket)
    }
    if (webConfig.unix_domain_sockets != null) {
      socketConfigs.addAll(webConfig.unix_domain_sockets)
    }
    socketConfigs.stream().forEach() { socketConfig ->
      val udsConnFactories = mutableListOf<ConnectionFactory>()
      udsConnFactories.add(HttpConnectionFactory(httpConfig))
      if (socketConfig.h2c == true) {
        val http2 = HTTP2CServerConnectionFactory(httpConfig)
        http2.rateControlFactory = http2RateControlFactory
        udsConnFactories.add(http2)
      }

      if (isJEP380Supported(socketConfig.path)) {
        logger.info("Using UnixDomainServerConnector for ${socketConfig.path}")
        val udsConnector = UnixDomainServerConnector(
          server,
          null /* executor */,
          null /* scheduler */,
          null /* buffer pool */,
          webConfig.acceptors ?: -1,
          webConfig.selectors ?: -1,
          *udsConnFactories.toTypedArray()
        )
        val socketFile = File(socketConfig.path)
        udsConnector.unixDomainPath = socketFile.toPath()
        udsConnector.addBean(connectionMetricsCollector.newConnectionListener("http", 0))
        udsConnector.name = "uds"

        // try to clean up any leftover socket files before connecting
        if (socketFile.exists() && !socketFile.delete()) {
          logger.warn("Could not delete file $socketFile")
        }

        // set file permissions after socket creation so sidecars (e.g. envoy, istio) have access
        try {
          udsConnector.start()
          setFilePermissions(socketFile)
        } catch (e: Exception) {
          cleanAndThrow(udsConnector, e)
        }
        server.addConnector(udsConnector)
      } else {
        val udsConnector = UnixSocketConnector(
          server,
          null /* executor */,
          null /* scheduler */,
          null /* buffer pool */,
          webConfig.selectors ?: -1,
          *udsConnFactories.toTypedArray()
        )
        udsConnector.setUnixSocket(socketConfig.path)
        udsConnector.addBean(connectionMetricsCollector.newConnectionListener("http", 0))
        udsConnector.name = "uds"
        server.addConnector(udsConnector)
      }
    }

    // TODO(mmihic): Force security handler?
    val servletContextHandler = ServletContextHandler()
    servletContextHandler.addServlet(ServletHolder(webActionsServlet), "/*")

    JettyWebSocketServletContainerInitializer.configure(servletContextHandler, null)
    server.addManaged(servletContextHandler)

    statisticsHandler.handler = servletContextHandler
    statisticsHandler.server = server

    // Kubernetes sends a SIG_TERM and gives us 30 seconds to stop gracefully.
    server.stopTimeout = 25_000
    val serverStats = ConnectionStatistics()
    server.addBean(serverStats)

    gzipHandler.server = server
    if (webConfig.gzip) {
      gzipHandler.minGzipSize = webConfig.minGzipSize
      gzipHandler.addIncludedMethods("POST")
      gzipHandler.addExcludedMimeTypes(MediaTypes.APPLICATION_GRPC)
    } else {
      // GET is enabled by default for gzipHandler.
      gzipHandler.addExcludedMethods("GET", "POST")
    }
    servletContextHandler.insertHandler(gzipHandler)

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

  fun stop() {
    if (server.isRunning) {
      val stopwatch = Stopwatch.createStarted()
      logger.info("Stopping Jetty")

      try {
        server.stop()
      } catch (_: InvalidPathException) {
        // Currently we get a nul character exception since an abstract socket address is
        // distinguished from a regular unix socket by the fact that the first byte of
        // the address is a null byte ('\0'). The address has no connection with filesystem
        // path names.
      }

      logger.info { "Stopped Jetty in $stopwatch" }
    }

    if (healthExecutor != null) {
      healthExecutor!!.shutdown()
      healthExecutor!!.awaitTermination(10, TimeUnit.SECONDS)
    }
  }

  override fun shutDown() {
    if (webConfig.jettyHealthServiceEnabled()) {
      // We will keep the health instance alive so the server continues to report liveness
      // until graceful shutdown is complete.  It is therefore safe to gracefully shut down the
      // web service while we continue orderly graceful cleanup.
      stop()
    } else if (webConfig.shutdown_sleep_ms > 0) {
      // We need jetty to shut down at the very end to keep outbound connections alive
      // (due to sidecars). As such, we wait for `shutdown_sleep_ms` so that our
      // in flight requests drain, but we don't shut down dependencies until after.
      //
      // The true jetty shutdown occurs in stop() above, called from MiskApplication.
      //
      // Ideally we could call jetty.awaitInflightRequests() but that's not available
      // for us.
      //
      // Default is to shutdown jetty after all guava managed services are shutdown.
      sleep(webConfig.shutdown_sleep_ms.toLong())
    } else {
      stop()
    }
  }

  companion object {
    private val logger = getLogger<JettyService>()
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

internal fun NetworkConnector.toHttpUrl(): HttpUrl {
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

private fun AbstractHTTP2ServerConnectionFactory.customize(webConfig: WebConfig) {
  if (webConfig.jetty_max_concurrent_streams != null) {
    maxConcurrentStreams = webConfig.jetty_max_concurrent_streams
  }
  if (webConfig.jetty_initial_session_recv_window != null) {
    initialSessionRecvWindow = webConfig.jetty_initial_session_recv_window
  }
  if (webConfig.jetty_initial_stream_recv_window != null) {
    initialStreamRecvWindow = webConfig.jetty_initial_stream_recv_window
  }
}

/**
 * JEP-380 is supported when running Java 16+ and the provided socket path is non-abstract. Abstract
 * socket paths are identified by paths prefixed with an `@` symbol or a null byte.
 */
internal fun isJEP380Supported(
  path: String,
  javaVersion: Int = JavaVersion.VERSION.major
): Boolean {
  return javaVersion >= 16 &&
    !Strings.isNullOrEmpty(path) &&
    !Pattern.compile("^@|\u0000").matcher(path).find()
}

private fun setFilePermissions(file: File) {
  try {
    Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-rw-rw-"))
  } catch (e: IOException) {
    throw RuntimeException(e)
  }
}

private fun cleanAndThrow(connector: Connector, exception: Exception) {
  val runtimeException = RuntimeException(exception)
  if (connector.isStarted()) {
    try {
      connector.stop()
    } catch (e: Exception) {
      runtimeException.addSuppressed(e)
    }
  }
  throw runtimeException
}
