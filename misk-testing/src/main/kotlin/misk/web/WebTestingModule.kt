package misk.web

import misk.MiskTestingServiceModule
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule
import misk.security.ssl.CertStoreConfig
import misk.security.ssl.SslLoader
import okhttp3.internal.concurrent.TaskRunner
import okhttp3.internal.http2.Http2
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArraySet
import java.util.logging.ConsoleHandler
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import kotlin.reflect.KClass

/**
 * A module that starts an embedded Jetty web server configured for testing. The server supports
 * both plaintext and TLS.
 */
@Deprecated(
  message = "It's replace by WebServerTestingModule + MiskTestingServiceModule to facilitate" +
    "the composability of testing modules for application owners",
  replaceWith = ReplaceWith(expression = "WebServerTestingModule", "misk.web")
)
class WebTestingModule(
  private val webConfig: WebConfig = TESTING_WEB_CONFIG
) : KAbstractModule() {
  override fun configure() {
    install(WebServerTestingModule(webConfig))
    install(MiskTestingServiceModule())
  }
  companion object {
    val TESTING_WEB_CONFIG =  WebServerTestingModule.TESTING_WEB_CONFIG
  }
}

/**
 * A module that starts an embedded Jetty web server configured for testing. The server supports
 * both plaintext and TLS.
 */
class WebServerTestingModule(
  private val webConfig: WebConfig = TESTING_WEB_CONFIG
) : KAbstractModule() {
  override fun configure() {
    install(EnvironmentModule(Environment.TESTING))
    install(MiskWebModule(webConfig))
    OkHttpDebugLogging.enableHttp2()
    OkHttpDebugLogging.enableTaskRunner()
  }

  companion object {
    val TESTING_WEB_CONFIG = WebConfig(
      // 0 results in a random port
      port = 0,
      health_port = 0,
      // use a deterministic number for selector/acceptor threads since the dynamic number can
      // vary local vs CI. this allows writing thread exhaustion tests.
      acceptors = 1,
      selectors = 1,
      idle_timeout = 500000,
      host = "127.0.0.1",
      close_connection_percent = 0.00,
      ssl = WebSslConfig(
        port = 0,
        cert_store = CertStoreConfig(
          resource = "classpath:/ssl/server_cert_key_combo.pem",
          passphrase = "serverpassword",
          format = SslLoader.FORMAT_PEM
        ),
        mutual_auth = WebSslConfig.MutualAuth.NONE
      )
    )
  }
}

object OkHttpDebugLogging {
  // Keep references to loggers to prevent their configuration from being GC'd.
  private val configuredLoggers = CopyOnWriteArraySet<Logger>()

  fun enableHttp2() = enable(Http2::class)

  fun enableTaskRunner() = enable(TaskRunner::class)

  fun logHandler() = ConsoleHandler().apply {
    level = Level.FINE
    formatter = object : SimpleFormatter() {
      override fun format(record: LogRecord) =
        String.format("[%1\$tF %1\$tT] %2\$s %n", record.millis, record.message)
    }
  }

  fun enable(loggerClass: String, handler: Handler = logHandler()): Closeable {
    val logger = Logger.getLogger(loggerClass)
    if (configuredLoggers.add(logger)) {
      logger.addHandler(handler)
      logger.level = Level.FINEST
    }
    return Closeable {
      logger.removeHandler(handler)
    }
  }

  fun enable(loggerClass: KClass<*>) = enable(loggerClass.java.name)
}
