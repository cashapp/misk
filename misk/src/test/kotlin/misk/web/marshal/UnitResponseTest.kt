package misk.web.marshal

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.WebTestClient
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class UnitResponseTest {
  @MiskTestModule val module = TestModule()

  @Inject private lateinit var webTestClient: WebTestClient

  @Test
  fun returnUnitResponseBody() {
    with(webTestClient.get("/response/as-unit-response-body")) {
      assertThat(response.code).isEqualTo(200)
      assertThat(response.headers["Content-Type"]).isNull()
      assertThat(response.body!!.bytes()).isEmpty()
    }
  }

  @Test
  fun returnEmptyStringResponseBody() {
    with(webTestClient.get("/response/as-no-response-content-type")) {
      assertThat(response.code).isEqualTo(200)
      assertThat(response.headers["Content-Type"]).isNull()
      assertThat(response.body!!.bytes()).isEmpty()
    }
  }

  @Test
  fun returnWithContentType() {
    with(webTestClient.get("/response/as-application-json")) {
      assertThat(response.code).isEqualTo(200)
      assertThat(response.headers["Content-Type"]).isEqualTo(MediaTypes.APPLICATION_JSON)
      assertThat(response.body!!.bytes()).isEmpty()
    }
  }

  class ReturnAsUnitResponseBody @Inject constructor() : WebAction {
    @Get("/response/as-unit-response-body") fun call() = Response(Unit)
  }

  class ReturnAsEmptyStringResponseBody @Inject constructor() : WebAction {
    @Get("/response/as-no-response-content-type") fun call() = ""
  }

  class ReturnWithContentType @Inject constructor() : WebAction {
    @Get("/response/as-application-json") @ResponseContentType(MediaTypes.APPLICATION_JSON) fun call() = Response(Unit)
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<ReturnAsUnitResponseBody>())
      install(WebActionModule.create<ReturnAsEmptyStringResponseBody>())
      install(WebActionModule.create<ReturnWithContentType>())
    }
  }
}
