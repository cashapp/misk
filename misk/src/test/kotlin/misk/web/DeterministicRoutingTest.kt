package misk.web

import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Collections.shuffle
import javax.inject.Inject

@MiskTest(startService = true)
internal class DeterministicRoutingTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var jettyService: JettyService

  @Test
  fun picksMostSpecificPaths() {
    assertThat(get("/org/admin/users")).isEqualTo("specific-path-action")
    assertThat(get("/org/admin/foo")).isEqualTo("subsection-action")
    assertThat(get("/org/foo/bar")).isEqualTo("section-action")
    assertThat(get("/org/admin/users/bar")).isEqualTo("remainder-path-action")
    assertThat(get("/org/unknown/caller/deep/path")).isEqualTo("whole-path")
  }

  class SpecificPathAction @Inject constructor() : WebAction {
    @Get("/org/admin/users")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun handle() = "specific-path-action"
  }

  class SubsectionAction @Inject constructor() : WebAction {
    @Get("/org/admin/{subsection}")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun handle() = "subsection-action"
  }

  class SectionAction @Inject constructor() : WebAction {
    @Get("/org/{section}/{subsection}")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun handle() = "section-action"
  }

  class RemainderPathAction @Inject constructor() : WebAction {
    @Get("/org/admin/{path:.*}")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun handle() = "remainder-path-action"
  }

  class WholePathAction @Inject constructor() : WebAction {
    @Get("/{path:.*}")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun handle() = "whole-path"
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      val webActions = mutableListOf(
        WholePathAction::class,
        RemainderPathAction::class,
        SectionAction::class,
        SubsectionAction::class,
        SpecificPathAction::class
      )
      shuffle(webActions)
      for (webAction in webActions) {
        install(WebActionModule.create(webAction))
      }
    }
  }

  private fun get(path: String): String = call(
    Request.Builder()
      .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
      .get()
  )

  private fun call(request: Request.Builder): String {
    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request.build()).execute()
    return response.body!!.string()
  }
}
