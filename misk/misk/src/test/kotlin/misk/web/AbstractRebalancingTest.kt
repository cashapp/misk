package misk.web

import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

abstract class AbstractRebalancingTest(
  val percent: Double
) {
  @MiskTestModule
  val module = TestModule()

  @Inject
  lateinit var jettyService: JettyService

  @Test
  fun rebalancing() {
    val httpClient = OkHttpClient()
    val request = Request.Builder()
        .url(jettyService.httpServerUrl)
        .build()

    val response = httpClient.newCall(request).execute()
    checkResponse(response)
  }

  abstract fun checkResponse(response: Response)

  inner class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule(webConfig = WebTestingModule.TESTING_WEB_CONFIG.copy(
          close_connection_percent = percent
      )))
    }
  }
}

@MiskTest(startService = true)
class RebalancingEnabledTest : AbstractRebalancingTest(100.0) {
  override fun checkResponse(response: Response) {
    assertThat(response.header("Connection")).isEqualTo("close")
  }
}

@MiskTest(startService = true)
class RebalancingDisabledTest : AbstractRebalancingTest(0.0) {
  override fun checkResponse(response: Response) {
    assertThat(response.header("Connection")).isNull()
  }
}
