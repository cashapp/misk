package misk.web.actions

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.util.Modules
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.ClockModule
import misk.web.FakeService
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.jetty.JettyService
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

@MiskTest(startService = true)
class LivenessCheckActionTest {
  @MiskTestModule
  val module = TestModule()
  class TestModule : KAbstractModule() {
    override fun configure() {
      install(Modules.override(MiskTestingServiceModule()).with(ClockModule()))
      install(WebServerTestingModule())
      install(WebActionModule.create<LivenessCheckAction>())
      install(ServiceModule<FakeService>().enhancedBy<ReadyService>())
    }
  }

  @Inject lateinit var jettyService: JettyService
  @Inject lateinit var serviceManager: ServiceManager

  private val client = OkHttpClient().newBuilder()
    .readTimeout(Duration.ofSeconds(30))
    .connectTimeout(Duration.ofSeconds(30))
    .writeTimeout(Duration.ofSeconds(30))
    .build()

  /**
   * Liveness should only fail when Jetty is shut down.
   */
  @Test
  fun liveness() {
    val url = jettyService.httpServerUrl.resolve("/_liveness")!!
    assertThat(get(url).code).isEqualTo(200)
    serviceManager.stopAsync()
    serviceManager.awaitStopped()
    assertThat(get(url).code).isEqualTo(200)
    jettyService.stop()
    assertThatThrownBy { get(url)}
  }

  private fun get(url: HttpUrl) : Response{
    val req = Request.Builder().url(url).build();
    return client.newCall(req).execute();
  }
}
