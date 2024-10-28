package misk.web.health

import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Logger
import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.util.Modules
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.MiskApplication
import misk.MiskTestingServiceModule
import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.time.ClockModule
import misk.web.Get
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.WebServerTestingModule.Companion.TESTING_WEB_CONFIG
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.slf4j.LoggerFactory
import wisp.logging.getLogger
import java.lang.Thread.sleep
import java.net.ConnectException
import java.net.ServerSocket
import java.net.SocketOptions
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

// Must run isolated because it binds an available port to discover it and then releases it
// for misk to use.
@Isolated
class MiskApplicationHealthServiceTest {
  var healthPort: Int = -1
  var webPort: Int = -1

  inner class TestModule : KAbstractModule() {
    override fun configure() {
      val healthSocket = ServerSocket(0, SocketOptions.SO_REUSEPORT)
      val jettySocket = ServerSocket(0, SocketOptions.SO_REUSEPORT)

      healthPort = healthSocket.localPort
      webPort = jettySocket.localPort

      healthSocket.close()
      jettySocket.close()

      install(Modules.override(MiskTestingServiceModule()).with(ClockModule()))
      install(
        WebServerTestingModule(
          TESTING_WEB_CONFIG.copy(
            port = webPort,
            health_port = healthPort,
            health_dedicated_jetty_instance = true
          )
        )
      )

      // An action that just returns ok.
      install(WebActionModule.create<AppTestHelloAction>())

      // Waits for a latch to be counted down before starting.  Will delay the
      // health service starting which happens after all other service start.
      install(ServiceModule<DelayHealthStart>().dependsOn<JettyStartedService>())

      // Depends on Jetty and once started counts down a latch.
      install(ServiceModule<JettyStartedService>().dependsOn<JettyService>())

      // Waits for a latch to be counted down before starting.
      // Counts down a latch when stopping to indicate Jetty has now shut down.
      install(ServiceModule<DelayJettyStartNotifyStop>().enhancedBy<ReadyService>())

      // Counts down a latch when started to indicate health service has begun starting.
      // Waits for a latch to be counted down before shutting down.
      install(ServiceModule<HealthCheckNotifyStartDelayStop>()
        .enhancedBy<DelayJettyStartNotifyStop>())
    }
  }

  private val client = OkHttpClient().newBuilder()
    .readTimeout(Duration.ofSeconds(5))
    .connectTimeout(Duration.ofSeconds(5))
    .writeTimeout(Duration.ofSeconds(5))
    .build()

  @Test
  fun health() {
    val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as? Logger
    root?.let { it.level = INFO }

    val miskShutdownLatch = CountDownLatch(1)
    val miskApplication = MiskApplication(TestModule())

    thread {
      logger.debug("Running Misk!")
      miskApplication.doRun(arrayOf())
      logger.debug("Misk shutdown!")
      miskShutdownLatch.countDown()
    }

    // Both services should be shutdown
    DelayJettyStartNotifyStop.startingLatch.await(10, TimeUnit.SECONDS)

    val webLivenessUrl = "http://127.0.0.1:$webPort/_liveness".toHttpUrl()
    val webReadinessUrl = "http://127.0.0.1:$webPort/_readiness".toHttpUrl()
    val webStatusUrl = "http://127.0.0.1:$webPort/_status".toHttpUrl()
    val webHelloUrl = "http://127.0.0.1:$webPort/hello".toHttpUrl()

    val healthLivenessUrl = "http://127.0.0.1:$healthPort/_liveness".toHttpUrl()
    val healthReadinessUrl = "http://127.0.0.1:$healthPort/_readiness".toHttpUrl()
    val healthStatusUrl = "http://127.0.0.1:$healthPort/_status".toHttpUrl()
    val healthHelloUrl = "http://127.0.0.1:$healthPort/hello".toHttpUrl()

    logger.info("--- Not Started ---")
    get(webHelloUrl, "web", expectThrowable = ConnectException::class)
    get(webLivenessUrl, "web", expectThrowable = ConnectException::class)
    get(webReadinessUrl, "web", expectThrowable = ConnectException::class)
    get(webStatusUrl, "web", expectThrowable = ConnectException::class)

    get(healthHelloUrl, "health", expectThrowable = ConnectException::class)
    get(healthLivenessUrl, "health", expectThrowable = ConnectException::class)
    get(healthReadinessUrl, "health", expectThrowable = ConnectException::class)
    get(healthStatusUrl, "health", expectThrowable = ConnectException::class)

    // Jetty start has been delayed by DelayJettyStartNotifyStop, inform it that it is
    // now ok to start.
    DelayJettyStartNotifyStop.okToStartUp.countDown()

    // JettyStartedService dependsOn jetty so once it has started we know jetty has started.
    JettyStartedService.startedLatch.await(5, TimeUnit.SECONDS)

    logger.info("--- Web Server Up ---")
    get(webHelloUrl, "web", expectCode = 200)
    get(webLivenessUrl, "web", expectCode = 200)
    get(webReadinessUrl, "web", expectCode = 503)
    get(webStatusUrl, "web", expectCode = 200)

    get(healthHelloUrl, "health", expectThrowable = ConnectException::class)
    get(healthLivenessUrl, "health", expectThrowable = ConnectException::class)
    get(healthReadinessUrl, "health", expectThrowable = ConnectException::class)
    get(healthStatusUrl, "health", expectThrowable = ConnectException::class)

    // Make sure all services are up before proceeding.
    val serviceManager = miskApplication.injector.getInstance(ServiceManager::class.java)
    serviceManager.awaitHealthy()

    // Health service will start after all running services so allow it to start.
    DelayHealthStart.okToStartUp.countDown()
    sleep(5.seconds.inWholeMilliseconds)

    logger.info("--- Health Server Up ---")
    get(webHelloUrl, "web", expectCode = 200)
    get(webLivenessUrl, "web", expectCode = 200)
    get(webReadinessUrl, "web", expectCode = 200)
    get(webStatusUrl, "web", expectCode = 200)

    get(healthHelloUrl, "health", expectCode = 404)
    get(healthLivenessUrl, "health", expectCode = 200)
    get(healthReadinessUrl, "health", expectCode = 200)
    get(healthStatusUrl, "health", expectCode = 200)

    // Begin the shutdown sequence by invoking the shutdown hook.
    miskApplication.shutdownHook.start()

    DelayJettyStartNotifyStop.shutdownLatch.await(10, TimeUnit.SECONDS)

    logger.info("--- WebServer Down ---")
    get(webHelloUrl, "web", expectThrowable = ConnectException::class)
    get(webLivenessUrl, "web", expectThrowable = ConnectException::class)
    get(webReadinessUrl, "web", expectThrowable = ConnectException::class)
    get(webStatusUrl, "web", expectThrowable = ConnectException::class)

    get(healthHelloUrl, "health", expectCode = 404)
    get(healthLivenessUrl, "health", expectCode = 200)
    get(healthReadinessUrl, "health", expectCode = 503)
    get(healthStatusUrl, "health", expectCode = 200)

    // Let the HealthService Shutdown, wait for misk to shut down.
    HealthCheckNotifyStartDelayStop.okToShutdownLatch.countDown()
    assertThat(miskShutdownLatch.await(10, TimeUnit.SECONDS)).isTrue()

    logger.info("--- HealthServer Down ---")
    get(webHelloUrl, "web", expectThrowable = ConnectException::class)
    get(webLivenessUrl, "web", expectThrowable = ConnectException::class)
    get(webReadinessUrl, "web", expectThrowable = ConnectException::class)
    get(webStatusUrl, "web", expectThrowable = ConnectException::class)

    get(healthHelloUrl, "health", expectThrowable = ConnectException::class)
    get(healthLivenessUrl, "health", expectThrowable = ConnectException::class)
    get(healthReadinessUrl, "health", expectThrowable = ConnectException::class)
    get(healthStatusUrl, "health", expectThrowable = ConnectException::class)
  }

  private fun get(
    url: HttpUrl,
    host: String,
    expectCode: Int? = null,
    expectThrowable: KClass<*>? = null
  ): String {
    val req = Request.Builder().url(url).build()
    var result = ""
    var response: Response? = null
    try {
      response = client.newCall(req).execute()
      result = response.code.toString()

      assertThat(response.code).isEqualTo(expectCode)

      return result
    } catch (e: Exception) {
      assertThat(e).isInstanceOf(expectThrowable!!.java)

      result = "${e::class.simpleName}: ${e.message.toString()}"
      return result
    } finally {
      response?.close()
      logger.info("$host - url: $url - $result")
    }
  }

  @Singleton
  internal class AppTestHelloAction @Inject constructor() : WebAction {
    @Get("/hello")
    fun get(): String {
      return "success"
    }
  }

  @Singleton
  internal class JettyStartedService @Inject constructor() : AbstractIdleService() {
    override fun startUp() {
      logger.info { "Starting up" }
      startedLatch.countDown()
      logger.info { "Started " }
    }

    override fun shutDown() {
      logger.info { "Shutting down" }
      logger.info { "Shutdown" }
    }

    companion object {
      private val logger = getLogger<JettyStartedService>()
      val startedLatch = CountDownLatch(1)
    }
  }

  @Singleton
  internal class DelayJettyStartNotifyStop @Inject constructor() : AbstractIdleService() {
    override fun startUp() {
      logger.info { "Starting up" }
      startingLatch.countDown()
      okToStartUp.await(20, TimeUnit.SECONDS)
      logger.info { "Started " }
    }

    override fun shutDown() {
      logger.info { "Shutting down" }
      shutdownLatch.countDown()
      logger.info { "Shutdown" }
    }

    companion object {
      private val logger = getLogger<DelayJettyStartNotifyStop>()
      val okToStartUp = CountDownLatch(1)
      val startingLatch = CountDownLatch(1)
      val shutdownLatch = CountDownLatch(1)
    }
  }

  @Singleton
  internal class DelayHealthStart @Inject constructor() : AbstractIdleService() {
    override fun startUp() {
      logger.info { "Starting up" }
      okToStartUp.await(20, TimeUnit.SECONDS)
      logger.info { "Started " }
    }

    override fun shutDown() {
      logger.info { "Shutdown" }
    }

    companion object {
      private val logger = getLogger<DelayHealthStart>()
      val okToStartUp = CountDownLatch(1)
    }
  }

  @Singleton
  internal class HealthCheckNotifyStartDelayStop @Inject constructor() : AbstractIdleService() {
    override fun startUp() {
      logger.info { "Started" }
    }

    override fun shutDown() {
      logger.info { "Shutting down" }
      okToShutdownLatch.await(20, TimeUnit.SECONDS)
      logger.info { "Shutdown" }
    }

    companion object {
      private val logger = getLogger<HealthCheckNotifyStartDelayStop>()
      val okToShutdownLatch = CountDownLatch(1)
    }
  }

  companion object {
    val logger = getLogger<MiskApplicationHealthServiceTest>()
  }
}
