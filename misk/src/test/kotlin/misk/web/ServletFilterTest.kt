package misk.web

import jakarta.inject.Inject
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Verifies the [jakarta.servlet.Filter] multibind runs *before* [misk.web.jetty.WebActionsServlet] dispatches, so
 * application filters can rewrite the request URL before route matching. This is the capability [NetworkInterceptor]
 * cannot provide (those run after an action is already matched).
 */
@MiskTest(startService = true)
class ServletFilterTest {
  @MiskTestModule val module = TestModule()

  @Inject lateinit var jetty: JettyService

  @Test
  fun `exact path still matches without any rewriting`() {
    assertThat(get("/filter-target").code).isEqualTo(200)
  }

  @Test
  fun `bound filter rewrites the url before routing so a trailing slash matches`() {
    // "/filter-target/" does not match @Get("/filter-target"); it only returns 200 because the
    // multibound filter strips the trailing slash before WebActionsServlet matches the route.
    assertThat(get("/filter-target/").code).isEqualTo(200)
  }

  private fun get(path: String): Response =
    OkHttpClient()
      .newCall(Request.Builder().url(jetty.httpServerUrl.newBuilder().encodedPath(path).build()).get().build())
      .execute()

  class TargetAction @Inject constructor() : WebAction {
    @Get("/filter-target") @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8) fun get(): String = "ok"
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<TargetAction>())
      multibind<Filter>().to<TrailingSlashFilter>()
    }
  }
}

/**
 * Control test: without the filter bound, a trailing-slash path does not match the exact route and falls through to
 * a 404. This isolates the behavior added by the filter above.
 */
@MiskTest(startService = true)
class ServletFilterAbsentTest {
  @MiskTestModule val module = TestModule()

  @Inject lateinit var jetty: JettyService

  @Test
  fun `trailing slash returns 404 when no rewriting filter is bound`() {
    val response =
      OkHttpClient()
        .newCall(
          Request.Builder().url(jetty.httpServerUrl.newBuilder().encodedPath("/filter-target/").build()).get().build()
        )
        .execute()
    assertThat(response.code).isEqualTo(404)
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<ServletFilterTest.TargetAction>())
    }
  }
}

/** Strips a single trailing slash from the request URL before Misk matches the route. */
private class TrailingSlashFilter @Inject constructor() : Filter {
  override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    if (request is HttpServletRequest) {
      val uri = request.requestURI
      if (uri.length > 1 && uri.endsWith("/")) {
        chain.doFilter(StrippedRequest(request, uri.dropLast(1)), response)
        return
      }
    }
    chain.doFilter(request, response)
  }

  private class StrippedRequest(delegate: HttpServletRequest, private val uri: String) :
    HttpServletRequestWrapper(delegate) {
    override fun getRequestURI(): String = uri

    // Misk derives the route-matching URL from getRequestURL(), so it must be rewritten too.
    override fun getRequestURL(): StringBuffer {
      val url = super.getRequestURL()
      if (url.isNotEmpty() && url.last() == '/') {
        url.deleteCharAt(url.length - 1)
      }
      return url
    }
  }
}
