package misk.web

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.util.Modules
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import misk.MiskTestingServiceModule
import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.logging.getLogger
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.ClockModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class GracefulShutdownTest {
  @MiskTestModule val module = TestModule()

  @Inject lateinit var helloAction: HelloAction
  @Inject lateinit var jettyService: JettyService
  @Inject lateinit var serviceManager: ServiceManager

  private val finishedLatch = CountDownLatch(1)
  private val httpClient =
    OkHttpClient()
      .newBuilder()
      .readTimeout(Duration.ofSeconds(30))
      .connectTimeout(Duration.ofSeconds(30))
      .writeTimeout(Duration.ofSeconds(30))
      .build()

  /**
   * This test initiates a long-running HTTP call and makes sure that even if we start the shutdown process during the
   * call that the client still receives a successful response.
   *
   * Additionally, the action itself relies on FakeService, so we verify that by having FakeService enhancedBy
   * ReadyService that JettyService stops before ReadyService does.
   */
  @Test
  fun basic() {
    makeHttpCall()
    helloAction.startedLatch.await(15, TimeUnit.SECONDS)
    serviceManager.stopAsync()
    finishedLatch.await(15, TimeUnit.SECONDS)
  }

  private fun makeHttpCall() {
    val url = jettyService.httpServerUrl.resolve("/hello")!!
    val request = Request.Builder().url(url).build()

    httpClient
      .newCall(request)
      .enqueue(
        object : Callback {
          override fun onFailure(call: Call, e: IOException) {}

          override fun onResponse(call: Call, response: Response) {
            if (response.code == 200) {
              finishedLatch.countDown()
            }
          }
        }
      )
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(Modules.override(MiskTestingServiceModule()).with(ClockModule()))
      install(WebServerTestingModule())
      install(WebActionModule.create<HelloAction>())
      install(ServiceModule<FakeService>().enhancedBy<ReadyService>())
    }
  }
}

@Singleton
internal class HelloAction @Inject constructor(private val fakeService: FakeService) : WebAction {
  val startedLatch = CountDownLatch(1)

  @Get("/hello")
  fun get(): String {
    startedLatch.countDown()
    Thread.sleep(Duration.ofSeconds(5).toMillis())
    fakeService.doWork()
    return "success"
  }
}

@Singleton
internal class FakeService @Inject constructor() : AbstractIdleService() {
  private var started = false

  fun doWork() {
    if (!started) {
      error("Hey I'm not even running!")
    }
  }

  override fun startUp() {
    logger.info { "Starting up" }
    started = true
  }

  override fun shutDown() {
    logger.info { "Shutting down" }
    started = false
  }

  companion object {
    private val logger = getLogger<FakeService>()
  }
}
