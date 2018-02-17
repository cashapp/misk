package misk.web

import com.google.inject.util.Modules
import misk.MiskModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestWebModule
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
  val module = Modules.combine(
      MiskModule(),
      WebModule(),
      TestWebModule(),
      TestModule()
  )

  private @Inject lateinit var jettyService: JettyService

  @Test
  fun picksMostSpecificPaths() {
    assertThat(get("/org/admin/users")).isEqualTo("specific-path-action")
    assertThat(get("/org/admin/foo")).isEqualTo("subsection-action")
    assertThat(get("/org/foo/bar")).isEqualTo("section-action")
    assertThat(get("/org/admin/users/bar")).isEqualTo("remainder-path-action")
    assertThat(get("/org/unknown/caller/deep/path")).isEqualTo("whole-path")
  }

  class SpecificPathAction : WebAction {
    @Get("/org/admin/users")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun handle() = "specific-path-action"
  }

  class SubsectionAction : WebAction {
    @Get("/org/admin/{subsection}")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun handle() = "subsection-action"
  }

  class SectionAction : WebAction {
    @Get("/org/{section}/{subsection}")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun handle() = "section-action"
  }

  class RemainderPathAction : WebAction {
    @Get("/org/admin/{path:.*}")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun handle() = "remainder-path-action"
  }

  class WholePathAction : WebAction {
    @Get("/{path:.*}")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun handle() = "whole-path"
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      val webActionModules = mutableListOf(
          WebActionModule.create<WholePathAction>(),
          WebActionModule.create<RemainderPathAction>(),
          WebActionModule.create<SectionAction>(),
          WebActionModule.create<SubsectionAction>(),
          WebActionModule.create<SpecificPathAction>()
      )
      shuffle(webActionModules)
      webActionModules.forEach { install(it) }
    }
  }

  private fun get(path: String): String = call(
      Request.Builder()
          .url(jettyService.serverUrl.newBuilder().encodedPath(path).build())
          .get()
  )

  private fun call(request: Request.Builder): String {
    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request.build())
        .execute()
    return response.body()!!.string()
  }

}
