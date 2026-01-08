package misk.web.health

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.util.Modules
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.ConnectException
import java.time.Duration
import kotlin.reflect.KClass
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.logging.getLogger
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.ClockModule
import misk.web.Get
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.WebServerTestingModule.Companion.TESTING_WEB_CONFIG
import misk.web.actions.LivenessCheckAction
import misk.web.actions.ReadinessCheckAction
import misk.web.actions.WebAction
import misk.web.jetty.JettyHealthService
import misk.web.jetty.JettyService
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class JettyHealthServiceTest {
  @MiskTestModule val module = TestModule()

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(Modules.override(MiskTestingServiceModule()).with(ClockModule()))
      install(WebServerTestingModule(TESTING_WEB_CONFIG.copy(health_dedicated_jetty_instance = true)))
      install(WebActionModule.create<LivenessCheckAction>())
      install(WebActionModule.create<ReadinessCheckAction>())
      install(WebActionModule.create<JettyTestHelloAction>())
    }
  }

  @Inject lateinit var jettyService: JettyService
  @Inject internal lateinit var jettyHealthService: JettyHealthService
  @Inject lateinit var serviceManager: ServiceManager

  private val client =
    OkHttpClient()
      .newBuilder()
      .readTimeout(Duration.ofSeconds(5))
      .connectTimeout(Duration.ofSeconds(5))
      .writeTimeout(Duration.ofSeconds(5))
      .build()

  @Test
  fun health() {
    val webLivenessUrl = jettyService.httpServerUrl.resolve("/_liveness")!!
    val webReadinessUrl = jettyService.httpServerUrl.resolve("/_readiness")!!
    val webStatusUrl = jettyService.httpServerUrl.resolve("/_status")!!
    val webHelloUrl = jettyService.httpServerUrl.resolve("/hello")!!

    logger.info("--- WebServer Up ---")
    get(webHelloUrl, "web", 200)
    get(webLivenessUrl, "web", 200)
    get(webReadinessUrl, "web", 200)
    get(webStatusUrl, "web", 200)

    jettyHealthService.startAsync()
    jettyHealthService.awaitRunning()

    logger.info("--- HealthServer Up ---")
    val healthLivenessUrl = jettyHealthService.healthServerUrl?.resolve("/_liveness")!!
    val healthReadinessUrl = jettyHealthService.healthServerUrl?.resolve("/_readiness")!!
    val healthStatusUrl = jettyHealthService.healthServerUrl?.resolve("/_status")!!
    val healthHelloUrl = jettyHealthService.healthServerUrl?.resolve("/hello")!!

    get(webHelloUrl, "web", expectCode = 200)
    get(webLivenessUrl, "web", expectCode = 200)
    get(webReadinessUrl, "web", expectCode = 200)
    get(webStatusUrl, "web", expectCode = 200)

    get(healthHelloUrl, "health", expectCode = 404)
    get(healthLivenessUrl, "health", expectCode = 200)
    get(healthReadinessUrl, "health", expectCode = 200)
    get(healthStatusUrl, "health", expectCode = 200)

    serviceManager.stopAsync()
    serviceManager.awaitStopped()

    logger.info("--- WebServer Down ---")
    get(webHelloUrl, "web", expectThrowable = ConnectException::class)
    get(webLivenessUrl, "web", expectThrowable = ConnectException::class)
    get(webReadinessUrl, "web", expectThrowable = ConnectException::class)
    get(webStatusUrl, "web", expectThrowable = ConnectException::class)

    get(healthHelloUrl, "health", expectCode = 404)
    get(healthLivenessUrl, "health", expectCode = 200)
    get(healthReadinessUrl, "health", expectCode = 503)
    get(healthStatusUrl, "health", expectCode = 200)

    jettyHealthService.stopAsync()
    jettyHealthService.awaitTerminated()

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

  private fun get(url: HttpUrl, host: String, expectCode: Int? = null, expectThrowable: KClass<*>? = null): String {
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
      result = "${e::class.simpleName}: ${e.message}"
      return result
    } finally {
      response?.close()
      logger.info("$host - url: $url - $result")
    }
  }

  @Singleton
  internal class JettyTestHelloAction @Inject constructor() : WebAction {
    @Get("/hello")
    fun get(): String {
      return "success"
    }
  }

  companion object {
    val logger = getLogger<JettyHealthServiceTest>()
  }
}
