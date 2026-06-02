package misk.web

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class UrlMatcherTest {
  @MiskTestModule val module = TestModule()

  @Inject lateinit var urlMatcher: UrlMatcher

  @Test
  fun matchesExistingStaticPath() {
    assertThat(urlMatcher.hasBoundAction("http://example.com/hello")).isTrue()
  }

  @Test
  fun matchesExistingPathWithParameter() {
    assertThat(urlMatcher.hasBoundAction("http://example.com/user/123")).isTrue()
    assertThat(urlMatcher.hasBoundAction("http://example.com/user/abc")).isTrue()
  }

  @Test
  fun doesNotMatchNonExistentPath() {
    assertThat(urlMatcher.hasBoundAction("http://example.com/does/not/exist")).isFalse()
  }

  @Test
  fun doesNotMatchPartialPath() {
    assertThat(urlMatcher.hasBoundAction("http://example.com/use")).isFalse()
  }

  @Test
  fun handlesPathsWithQueryParameters() {
    // Query parameters should be ignored, only path matters
    assertThat(urlMatcher.hasBoundAction("http://example.com/hello?foo=bar")).isTrue()
  }

  @Test
  fun handlesHttpsUrls() {
    assertThat(urlMatcher.hasBoundAction("https://example.com/hello")).isTrue()
  }

  @Test
  fun handlesDifferentHosts() {
    assertThat(urlMatcher.hasBoundAction("http://different-host.com/hello")).isTrue()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<HelloAction>())
      install(WebActionModule.create<UserAction>())
    }
  }

  class HelloAction @Inject constructor() : WebAction {
    @Get("/hello") @ResponseContentType(MediaTypes.APPLICATION_JSON) fun hello() = "hello"
  }

  class UserAction @Inject constructor() : WebAction {
    @Get("/user/{id}")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun getUser(@PathParam("id") id: String) = "user $id"
  }
}
