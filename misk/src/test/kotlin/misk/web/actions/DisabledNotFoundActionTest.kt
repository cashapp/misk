package misk.web.actions

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebServerTestingModule
import misk.web.WebTestingModule.Companion.TESTING_WEB_CONFIG
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import misk.web.mediatype.asMediaType
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// This is a separate test from NotFoundActionTest because changing the WebConfig within a class
// is not worth the hassle.
@MiskTest(startService = true)
internal class DisabledNotFoundActionTest {
  @MiskTestModule val module = TestModule()

  val httpClient = OkHttpClient()

  @Inject private lateinit var jettyService: JettyService

  @Test
  fun defaultNotFoundActionCanBeDisabled() {
    val request = get("/unknown", MediaTypes.APPLICATION_JSON.asMediaType())
    val response = httpClient.newCall(request).execute()
    assertThat(response.code).isEqualTo(404)
    // This message is created by WebActionsServlet.sendNotFound, and not the NotFoundAction
    assertThat(response.body!!.string()).matches("Nothing found at GET http://127.0.0.1:[0-9]*/unknown")
  }

  private fun get(path: String, acceptedMediaType: MediaType? = null): Request {
    return Request.Builder()
      .get()
      .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
      .header("Accept", acceptedMediaType.toString())
      .build()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule(TESTING_WEB_CONFIG.copy(install_default_not_found_action = false)))
      install(MiskTestingServiceModule())
    }
  }
}
