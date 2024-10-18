package misk.web.shutdown

import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Logger
import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.Service.State.NEW
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.MiskApplication
import misk.MiskTestingServiceModule
import misk.ServiceModule
import misk.client.HTTP_INTERNAL_SERVER_ERROR
import misk.client.HTTP_SERVICE_UNAVAILABLE
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.time.FakeClock
import misk.web.Get
import misk.web.GracefulShutdownConfig
import misk.web.WebActionModule
import misk.web.WebConfig
import misk.web.WebServerTestingModule
import misk.web.WebServerTestingModule.Companion.TESTING_WEB_CONFIG
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.slf4j.LoggerFactory
import wisp.logging.getLogger
import java.lang.Thread.sleep
import java.net.ServerSocket
import java.net.SocketOptions
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

// Must run isolated because it binds an available port to discover it and then releases it
// for misk to use.
@Isolated
class GracefulShutdownInterceptorTest {
  private var healthPort: Int = -1
  private var webPort: Int = -1

  private lateinit var miskShutdownLatch : CountDownLatch

  private val defaultConfig = TESTING_WEB_CONFIG.copy(
    health_dedicated_jetty_instance = true
  )

  @BeforeEach
  fun setup() {
    val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as? Logger
    root?.let { it.level = INFO }

    miskShutdownLatch = CountDownLatch(1)
    JettyStartedService.startedLatch = CountDownLatch(1)
    InFlightAction.okToReturnLatch = CountDownLatch(1)
    InFlightAction.reachedActionLatch = CountDownLatch(1)

  }

  abstract inner class GracefulInterceptorModule : KAbstractModule() {
    abstract val config : WebConfig

    override fun configure() {
      val healthSocket = ServerSocket(0, SocketOptions.SO_REUSEPORT)
      val jettySocket = ServerSocket(0, SocketOptions.SO_REUSEPORT)

      healthPort = healthSocket.localPort
      webPort = jettySocket.localPort

      healthSocket.close()
      jettySocket.close()

      val testConfig = config.copy(
        port = webPort,
        health_port = healthPort
      )

      install(MiskTestingServiceModule())
      install(
        WebServerTestingModule(
          testConfig
        )
      )
      // An action that returns after awaiting a latch
      install(WebActionModule.create<InFlightAction>())

      // An action that just returns ok.
      install(WebActionModule.create<NotInflightAction>())

      install(GracefulShutdownModule(testConfig))

      install(ServiceModule<JettyStartedService>()
        .dependsOn<JettyService>())
    }
  }

  private val client = OkHttpClient().newBuilder()
    .readTimeout(Duration.ofSeconds(5))
    .connectTimeout(Duration.ofSeconds(5))
    .writeTimeout(Duration.ofSeconds(5))
    .build()

  /**
   * When graceful interceptor config is null the service and injector are not installed.
   */
  @Test
  fun nullConfig() {
    object : GracefulInterceptorModule() {
      override val config: WebConfig
        get() = defaultConfig.copy(
          graceful_shutdown_config = null
        )
    }.assertServiceAndInterceptorNotInstalled()
  }

  /**
   * When graceful interceptor config is disabled the service and injector are not installed.
   */
  @Test
  fun disabledConfig() {
    object : GracefulInterceptorModule() {
      override val config: WebConfig
        get() = defaultConfig.copy(
          graceful_shutdown_config = GracefulShutdownConfig(
            disabled = true
          )
        )
    }.assertServiceAndInterceptorNotInstalled()
  }

  /**
   * When the dedicated health service is disabled the service and injector are not installed.
   */
  @Test
  fun healthServiceNotEnabled() {
    object : GracefulInterceptorModule() {
      override val config: WebConfig
        get() = defaultConfig.copy(
          health_dedicated_jetty_instance = false,
          graceful_shutdown_config = GracefulShutdownConfig()
        )
    }.assertServiceAndInterceptorNotInstalled()
  }

  private fun GracefulInterceptorModule.assertServiceAndInterceptorNotInstalled() {
    val miskApplication = this.startUpMisk()

    val gracefulShutdownService = miskApplication.injector.getInstance<GracefulShutdownService>()
    assertThat(gracefulShutdownService.state()).isEqualTo(NEW)

    val inflightAction = "http://127.0.0.1:$webPort/inflight".toHttpUrl()

    // In flight action will remain in flight until okToReturnLatch
    getAsync(inflightAction)
    InFlightAction.reachedActionLatch.await(10, TimeUnit.SECONDS)

    // The interceptor should not be installed to track the in flight requests.
    assertThat(gracefulShutdownService.inFlightRequests).isEqualTo(0)

    InFlightAction.okToReturnLatch.countDown()

    miskApplication.shutdownHook.start()
    miskShutdownLatch.await(10, TimeUnit.SECONDS)
  }

  /**
   * Without idle timeout, tests that inflight requests are still waited on before proceeding.
   */
  @Test
  fun inflightRequest() {
    val miskApplication = object : GracefulInterceptorModule() {
      override val config: WebConfig
        get() = defaultConfig.copy(
          graceful_shutdown_config = GracefulShutdownConfig(
            disabled = false,
            idle_timeout = -1,
            max_graceful_wait = -1,
            rejection_status_code = HTTP_SERVICE_UNAVAILABLE
          )
        )
    }.startUpMisk()
    val inflightAction = "http://127.0.0.1:$webPort/inflight".toHttpUrl()
    val notInflightAction = "http://127.0.0.1:$webPort/notinflight".toHttpUrl()

    // In flight action will remain in flight until okToReturnLatch
    val inFlightResult = getAsync(inflightAction)
    InFlightAction.reachedActionLatch.await(10, TimeUnit.SECONDS)

    // Shutdown misk and wait for graceful shutdown service to be shutting down.
    miskApplication.shutdownHook.start()
    val gracefulShutdownService = miskApplication.injector.getInstance<GracefulShutdownService>()
    while (!gracefulShutdownService.shuttingDown) {
      sleep(100)
    }

    // Execute a new request while the original is inflight.
    val notInFlightResult = getAsync(notInflightAction)
    notInFlightResult.callCompleteLatch.await(10, TimeUnit.SECONDS)

    // Let the in flight request return
    InFlightAction.okToReturnLatch.countDown()
    inFlightResult.callCompleteLatch.await(10, TimeUnit.SECONDS)

    // In flight request should 200, the incoming request should have 503'd
    assertThat(inFlightResult.code).isEqualTo(200)
    assertThat(notInFlightResult.code).isEqualTo(503)
    assertThat(miskShutdownLatch.await(10, TimeUnit.SECONDS)).isTrue()
  }

  /**
   * Without max wait, ensures that incoming requests are idle before proceeding.
   */
  @Test
  fun notIdle() {
    val miskApplication = object : GracefulInterceptorModule() {
      override val config: WebConfig
        get() = defaultConfig.copy(
          graceful_shutdown_config = GracefulShutdownConfig(
            disabled = false,
            idle_timeout = 2000,
            max_graceful_wait = -1,
            rejection_status_code = HTTP_SERVICE_UNAVAILABLE
          )
        )
    }.startUpMisk()
    val notInflightAction = "http://127.0.0.1:$webPort/notinflight".toHttpUrl()

    // Shutdown misk and wait for graceful shutdown service to be shutting down.
    miskApplication.shutdownHook.start()
    val gracefulShutdownService = miskApplication.injector.getInstance<GracefulShutdownService>()
    while (!gracefulShutdownService.shuttingDown) {
      sleep(100)
    }

    // Requests should 503 and reset the idle timeout in between.
    var notInFlightResult = getAsync(notInflightAction)
    notInFlightResult.callCompleteLatch.await(10, TimeUnit.SECONDS)
    assertThat(notInFlightResult.code).isEqualTo(503)

    val fakeClock = miskApplication.injector.getInstance<FakeClock>()
    fakeClock.add(1, TimeUnit.SECONDS)

    assertThat(gracefulShutdownService.state() == Service.State.STOPPING)
    notInFlightResult = getAsync(notInflightAction)
    notInFlightResult.callCompleteLatch.await(10, TimeUnit.SECONDS)
    assertThat(notInFlightResult.code).isEqualTo(503)

    // Should still be stopping after 1 second since the last request reset idle.
    fakeClock.add(1, TimeUnit.SECONDS)
    assertThat(gracefulShutdownService.state() == Service.State.STOPPING)
    notInFlightResult = getAsync(notInflightAction)
    notInFlightResult.callCompleteLatch.await(10, TimeUnit.SECONDS)
    assertThat(notInFlightResult.code).isEqualTo(503)
    assertThat(gracefulShutdownService.state() == Service.State.STOPPING)

    // Should terminate after 2 seconds with no requests.
    fakeClock.add(2, TimeUnit.SECONDS)
    sleep(1000)

    assertThat(gracefulShutdownService.state() == Service.State.TERMINATED)
    assertThat(miskShutdownLatch.await(5, TimeUnit.SECONDS)).isTrue()
  }

  /**
   * Ensures custom status code is honored.
   */
  @Test
  fun customStatusCode() {
    val miskApplication = object : GracefulInterceptorModule() {
      override val config: WebConfig
        get() = defaultConfig.copy(
          graceful_shutdown_config = GracefulShutdownConfig(
            disabled = false,
            idle_timeout = 2000,
            max_graceful_wait = -1,
            rejection_status_code = HTTP_INTERNAL_SERVER_ERROR
          )
        )
    }.startUpMisk()
    val notInflightAction = "http://127.0.0.1:$webPort/notinflight".toHttpUrl()

    // Shutdown misk and wait for graceful shutdown service to be shutting down.
    miskApplication.shutdownHook.start()
    val gracefulShutdownService = miskApplication.injector.getInstance<GracefulShutdownService>()
    while (!gracefulShutdownService.shuttingDown) {
      sleep(100)
    }

    // Requests should 500 and reset the idle timeout in between.
    var notInFlightResult = getAsync(notInflightAction)
    notInFlightResult.callCompleteLatch.await(10, TimeUnit.SECONDS)
    assertThat(notInFlightResult.code).isEqualTo(500)

    val fakeClock = miskApplication.injector.getInstance<FakeClock>()
    fakeClock.add(1, TimeUnit.SECONDS)

    assertThat(gracefulShutdownService.state() == Service.State.STOPPING)
    notInFlightResult = getAsync(notInflightAction)
    notInFlightResult.callCompleteLatch.await(10, TimeUnit.SECONDS)
    assertThat(notInFlightResult.code).isEqualTo(500)

    // Should still be stopping after 1 second since the last request reset idle.
    fakeClock.add(1, TimeUnit.SECONDS)
    assertThat(gracefulShutdownService.state() == Service.State.STOPPING)
    notInFlightResult = getAsync(notInflightAction)
    notInFlightResult.callCompleteLatch.await(10, TimeUnit.SECONDS)
    assertThat(notInFlightResult.code).isEqualTo(500)
    assertThat(gracefulShutdownService.state() == Service.State.STOPPING)

    // Should terminate after 2 seconds with no requests.
    fakeClock.add(2, TimeUnit.SECONDS)
    sleep(1000)

    assertThat(gracefulShutdownService.state() == Service.State.TERMINATED)
    assertThat(miskShutdownLatch.await(5, TimeUnit.SECONDS)).isTrue()
  }

  /**
   * Ensures combined in flight and idle timeout is enforced together.
   */
  @Test
  fun idleWithInFlight() {
    val miskApplication = object : GracefulInterceptorModule() {
      override val config: WebConfig
        get() = defaultConfig.copy(
          graceful_shutdown_config = GracefulShutdownConfig(
            disabled = false,
            idle_timeout = 2000,
            max_graceful_wait = -1,
            rejection_status_code = HTTP_SERVICE_UNAVAILABLE
          )
        )
    }.startUpMisk()
    val notInflightAction = "http://127.0.0.1:$webPort/notinflight".toHttpUrl()
    val inflightAction = "http://127.0.0.1:$webPort/inflight".toHttpUrl()

    // In flight action will remain in flight until okToReturnLatch
    val inFlightResult = getAsync(inflightAction)
    InFlightAction.reachedActionLatch.await(10, TimeUnit.SECONDS)

    // Shutdown misk and wait for graceful shutdown service to be shutting down.
    miskApplication.shutdownHook.start()
    val gracefulShutdownService = miskApplication.injector.getInstance<GracefulShutdownService>()
    while (!gracefulShutdownService.shuttingDown) {
      sleep(100)
    }

    // Requests should 503 and reset the idle timeout in between.
    var notInFlightResult = getAsync(notInflightAction)
    notInFlightResult.callCompleteLatch.await(10, TimeUnit.SECONDS)
    assertThat(notInFlightResult.code).isEqualTo(503)

    val fakeClock = miskApplication.injector.getInstance<FakeClock>()
    fakeClock.add(1, TimeUnit.SECONDS)

    assertThat(gracefulShutdownService.state() == Service.State.STOPPING)
    notInFlightResult = getAsync(notInflightAction)
    notInFlightResult.callCompleteLatch.await(10, TimeUnit.SECONDS)
    assertThat(notInFlightResult.code).isEqualTo(503)

    // Should still be stopping after 1 second since the last request reset idle.
    fakeClock.add(1, TimeUnit.SECONDS)
    assertThat(gracefulShutdownService.state() == Service.State.STOPPING)
    notInFlightResult = getAsync(notInflightAction)
    notInFlightResult.callCompleteLatch.await(10, TimeUnit.SECONDS)
    assertThat(notInFlightResult.code).isEqualTo(503)
    assertThat(gracefulShutdownService.state() == Service.State.STOPPING)

    // Will be idle, but there is still a request in flight.
    fakeClock.add(2, TimeUnit.SECONDS)
    sleep(1000)
    assertThat(gracefulShutdownService.state() == Service.State.STOPPING)

    // Let the inflight request return and the graceful service should shut down.
    InFlightAction.okToReturnLatch.countDown()
    inFlightResult.callCompleteLatch.await(5, TimeUnit.SECONDS)
    assertThat(inFlightResult.code).isEqualTo(200)

    sleep(1000)
    assertThat(gracefulShutdownService.state() == Service.State.TERMINATED)

    assertThat(miskShutdownLatch.await(5, TimeUnit.SECONDS)).isTrue()
  }

  /**
   * Ensures waiting for idle and inflight is enforced even when rejection of incoming requests
   * is off.
   */
  @Test
  fun noRejects() {
    val miskApplication = object : GracefulInterceptorModule() {
      override val config: WebConfig
        get() = defaultConfig.copy(
          graceful_shutdown_config = GracefulShutdownConfig(
            disabled = false,
            idle_timeout = 2000,
            max_graceful_wait = -1,
            rejection_status_code = -1
          )
        )
    }.startUpMisk()

    val notInflightAction = "http://127.0.0.1:$webPort/notinflight".toHttpUrl()
    val inflightAction = "http://127.0.0.1:$webPort/inflight".toHttpUrl()

    // In flight action will remain in flight until okToReturnLatch
    val inFlightResult = getAsync(inflightAction)
    InFlightAction.reachedActionLatch.await(10, TimeUnit.SECONDS)

    // Shutdown misk and wait for graceful shutdown service to be shutting down.
    miskApplication.shutdownHook.start()
    val gracefulShutdownService = miskApplication.injector.getInstance<GracefulShutdownService>()
    while (!gracefulShutdownService.shuttingDown) {
      sleep(100)
    }

    // Requests should 200 and reset the idle timeout in between.
    var notInFlightResult = getAsync(notInflightAction)
    notInFlightResult.callCompleteLatch.await(10, TimeUnit.SECONDS)
    assertThat(notInFlightResult.code).isEqualTo(200)

    val fakeClock = miskApplication.injector.getInstance<FakeClock>()
    fakeClock.add(1, TimeUnit.SECONDS)

    // Will be idle, but there is still a request in flight.
    fakeClock.add(2, TimeUnit.SECONDS)
    sleep(1000)
    assertThat(gracefulShutdownService.state() == Service.State.STOPPING)

    // New incoming request is not rejected so idle time resets.
    notInFlightResult = getAsync(notInflightAction)
    notInFlightResult.callCompleteLatch.await(10, TimeUnit.SECONDS)
    assertThat(notInFlightResult.code).isEqualTo(200)

    // Let the inflight request return, but the service should not shut down due to non-rejected
    // incoming request.
    InFlightAction.okToReturnLatch.countDown()
    inFlightResult.callCompleteLatch.await(10, TimeUnit.SECONDS)
    assertThat(inFlightResult.code).isEqualTo(200)
    sleep(1000)
    assertThat(gracefulShutdownService.state() == Service.State.STOPPING)

    // No requests rejected or otherwise, should now stop.
    fakeClock.add(2, TimeUnit.SECONDS)
    sleep(1000)
    assertThat(gracefulShutdownService.state() == Service.State.STOPPING)

    assertThat(miskShutdownLatch.await(5, TimeUnit.SECONDS)).isTrue()
  }

  /**
   * Ensures that max graceful wait is enforced even when in flight requests and/or idle timeout
   * are not met.
   */
  @Test
  fun maxWait() {
    val miskApplication = object : GracefulInterceptorModule() {
      override val config: WebConfig
        get() = defaultConfig.copy(
          graceful_shutdown_config = GracefulShutdownConfig(
            disabled = false,
            idle_timeout = 2000,
            max_graceful_wait = 3000,
            rejection_status_code = HTTP_SERVICE_UNAVAILABLE
          )
        )
    }.startUpMisk()

    val notInflightAction = "http://127.0.0.1:$webPort/notinflight".toHttpUrl()

    // Shutdown misk and wait for graceful shutdown service to be shutting down.
    miskApplication.shutdownHook.start()
    val gracefulShutdownService = miskApplication.injector.getInstance<GracefulShutdownService>()
    while (!gracefulShutdownService.shuttingDown) {
      sleep(100)
    }

    // Requests should 503 and reset the idle timeout in between.
    var notInFlightResult = getAsync(notInflightAction)
    notInFlightResult.callCompleteLatch.await(10, TimeUnit.SECONDS)
    assertThat(notInFlightResult.code).isEqualTo(503)

    val fakeClock = miskApplication.injector.getInstance<FakeClock>()
    fakeClock.add(1, TimeUnit.SECONDS)

    assertThat(gracefulShutdownService.state() == Service.State.STOPPING)
    notInFlightResult = getAsync(notInflightAction)
    notInFlightResult.callCompleteLatch.await(10, TimeUnit.SECONDS)
    assertThat(notInFlightResult.code).isEqualTo(503)

    // Should still be stopping after 1 second since the last request reset idle.
    fakeClock.add(1, TimeUnit.SECONDS)
    assertThat(gracefulShutdownService.state() == Service.State.STOPPING)
    notInFlightResult = getAsync(notInflightAction)
    notInFlightResult.callCompleteLatch.await(10, TimeUnit.SECONDS)
    assertThat(notInFlightResult.code).isEqualTo(503)
    assertThat(gracefulShutdownService.state() == Service.State.STOPPING)

    // Should terminate after 1 second even though still idle due to max wait.
    fakeClock.add(1, TimeUnit.SECONDS)
    sleep(1000)

    assertThat(gracefulShutdownService.state() == Service.State.TERMINATED)
    assertThat(miskShutdownLatch.await(5, TimeUnit.SECONDS)).isTrue()
  }

  /**
   * Starts up misk on a separate thread and awaits started for the current config.
   */
  private fun GracefulInterceptorModule.startUpMisk() : MiskApplication {
    val miskApplication = MiskApplication(this)

    thread {
      logger.debug("Running Misk!")
      miskApplication.doRun(arrayOf())
      logger.debug("Misk shutdown!")
      miskShutdownLatch.countDown()
    }

    JettyStartedService.startedLatch.await(10, TimeUnit.SECONDS)
    return miskApplication
  }

  /**
   * The async result of an http request through getAsync.
   */
  private data class CallResult(
    var response : String? = null,
    var code : Int? = null,
    var ex: IOException? = null,
    var callCompleteLatch: CountDownLatch
  )

  /**
   * Enqueues the request and populates the CallResult on response, setting the callCompleteLatch
   * once completed.
   */
  private fun getAsync(
    url: HttpUrl
  ): CallResult {
    val req = Request.Builder().url(url).build()
    val callResult = CallResult(
      callCompleteLatch = CountDownLatch(1)
    )

    client.newCall(req).enqueue (object : Callback
    {
      override fun onFailure(call: Call, e: IOException) {
        callResult.ex = e
        logger.info { "call failed: $callResult" }
        callResult.callCompleteLatch.countDown()
      }

      override fun onResponse(call: Call, response: Response) {
        callResult.response = response.body.toString()
        callResult.code = response.code
        response.close()

        logger.info { "call succeeded: $callResult" }
        callResult.callCompleteLatch.countDown()
      }
    })

    return callResult
  }

  /**
   * Informs when the request reached the action through reachedActionLatch and waits for
   * okToReturnLatch before completing.  Allows verifying the behaviors with in flight requests.
   */
  @Singleton
  internal class InFlightAction @Inject constructor() : WebAction {
    @Get("/inflight")
    fun get(): String {
      reachedActionLatch.countDown()
      okToReturnLatch.await(10, TimeUnit.SECONDS)
      return "success"
    }

    companion object {
      lateinit var okToReturnLatch : CountDownLatch
      lateinit var reachedActionLatch : CountDownLatch
    }
  }

  /**
   * Plain old action that just returns
   */
  @Singleton
  internal class NotInflightAction @Inject constructor() : WebAction {
    @Get("/notinflight")
    fun get(): String {
      return "notinflight"
    }
  }

  /**
   * Sets a latch when started after jetty to allow the test to move forward with misk started.
   */
  @Singleton
  internal class JettyStartedService @Inject constructor() : AbstractIdleService() {
    override fun startUp() {
      startedLatch.countDown()
    }

    override fun shutDown() { }

    companion object {
      lateinit var startedLatch : CountDownLatch
    }
  }

  companion object {
    val logger = getLogger<GracefulShutdownInterceptorTest>()
  }
}
