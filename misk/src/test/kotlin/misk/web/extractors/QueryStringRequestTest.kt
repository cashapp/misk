package misk.web.extractors

import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.ConcurrencyLimitsOptOut
import misk.web.QueryParam
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class QueryStringRequestTest {
  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var jettyService: JettyService

  @Test fun basicParams() {
    assertThat(get("/basic-params", "str=foo&something=stuff&int=12&testEnum=ONE"))
        .isEqualTo("foo stuff 12 ONE basic-params")
  }

  @Test fun optionalParamsPresent() {
    assertThat(get("/optional-params", "str=foo&int=12"))
        .isEqualTo("foo 12 optional-params")
  }

  @Test fun optionalParamsNotPresent() {
    assertThat(get("/optional-params", "")).isEqualTo("null null optional-params")
  }

  @Test fun defaultParamsPresent() {
    assertThat(get("/default-params", "str=foo&int=12&testEnum=ONE"))
        .isEqualTo("foo 12 ONE default-params")
  }

  @Test fun defaultParamsNotPresent() {
    assertThat(get("/default-params", "")).isEqualTo("square 23 TWO default-params")
  }

  @Test fun listParams() {
    assertThat(get("/list-params", "strs=foo&strs=bar&ints=12&ints=42&strs=baz"))
        .isEqualTo("foo bar baz 12 42 list-params")
  }

  enum class TestEnum {
    ONE,
    TWO
  }

  class BasicParamsAction @Inject constructor() : WebAction {
    @Get("/basic-params")
    @ConcurrencyLimitsOptOut // TODO: Remove after 2020-08-01 (or use @AvailableWhenDegraded).
    fun call(
      @QueryParam str: String,
      @QueryParam("something") other: String,
      @QueryParam int: Int,
      @QueryParam testEnum: TestEnum
    ) = "$str $other $int $testEnum basic-params"
  }

  class OptionalParamsAction @Inject constructor() : WebAction {
    @Get("/optional-params")
    @ConcurrencyLimitsOptOut // TODO: Remove after 2020-08-01 (or use @AvailableWhenDegraded).
    fun call(@QueryParam str: String?, @QueryParam int: Int?) = "$str $int optional-params"
  }

  class DefaultParamsAction @Inject constructor() : WebAction {
    @Get("/default-params")
    @ConcurrencyLimitsOptOut // TODO: Remove after 2020-08-01 (or use @AvailableWhenDegraded).
    fun call(
      @QueryParam str: String = "square",
      @QueryParam int: Int = 23,
      @QueryParam testEnum: TestEnum = TestEnum.TWO
    ) = "$str $int $testEnum default-params"
  }

  class ListParamsAction @Inject constructor() : WebAction {
    @Get("/list-params")
    @ConcurrencyLimitsOptOut // TODO: Remove after 2020-08-01 (or use @AvailableWhenDegraded).
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(@QueryParam strs: List<String>, @QueryParam ints: List<Int>) = "${strs.joinToString(
        separator = " ")} " +
        "${ints.joinToString(separator = " ")} list-params"
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.create<BasicParamsAction>())
      install(WebActionModule.create<OptionalParamsAction>())
      install(WebActionModule.create<DefaultParamsAction>())
      install(WebActionModule.create<ListParamsAction>())
    }
  }

  private fun get(path: String, query: String): String = call(Request.Builder()
      .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).query(query).build())
      .get())

  private fun call(request: Request.Builder): String {
    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request.build()).execute()
    assertThat(response.code).isEqualTo(200)
    return response.body!!.source().readUtf8()
  }
}
