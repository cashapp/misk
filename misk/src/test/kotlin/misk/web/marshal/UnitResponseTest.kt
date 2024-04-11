package misk.web.marshal

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.Response
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.WebTestClient
import misk.web.actions.WebAction
import okhttp3.ResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class UnitResponseTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var webTestClient: WebTestClient

  @Test
  fun returnUnitResponseBody() {
    with(webTestClient.get("/response/as-unit-response-body")) {
      assertThat(response.code).isEqualTo(200)
      assertThat(response.headers.toMap()).doesNotContainKey("Content-Type")
      assertThat(response.body!!.bytes()).isEmpty()
    }
  }

  class ReturnAsUnitResponseBody @Inject constructor() : WebAction {
    @Get("/response/as-unit-response-body")
    fun call() = Response(Unit)
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<ReturnAsUnitResponseBody>())
    }
  }
}
