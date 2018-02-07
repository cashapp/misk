package misk.web.extractors

import com.google.inject.util.Modules
import com.squareup.moshi.Moshi
import misk.MiskModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestWebModule
import misk.web.*
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
  data class Packet(val message: String)

  @MiskTestModule
  val module = Modules.combine(
      MiskModule(),
      WebModule(),
      TestWebModule(),
      TestModule())

  private @Inject lateinit var moshi: Moshi
  private @Inject lateinit var jettyService: JettyService
  private val packetJsonAdapter get() = moshi.adapter(Packet::class.java)

  @Test
  fun basicParams() {
    assertThat(get("/basic-params", "str=foo&something=stuff&int=12&testEnum=ONE").message)
        .isEqualTo("foo stuff 12 ONE basic-params")
  }

  @Test
  fun optionalParamsPresent() {
    assertThat(get("/optional-params", "str=foo&int=12").message)
        .isEqualTo("foo 12 optional-params")
  }

  @Test
  fun optionalParamsNotPresent() {
    assertThat(get("/optional-params", "").message).isEqualTo("null null optional-params")
  }

  @Test
  fun defaultParamsPresent() {
    assertThat(get("/default-params", "str=foo&int=12&testEnum=ONE").message)
        .isEqualTo("foo 12 ONE default-params")
  }

  @Test
  fun defaultParamsNotPresent() {
    assertThat(get("/default-params", "").message).isEqualTo("square 23 TWO default-params")
  }

  @Test
  fun listParams() {
    assertThat(get("/list-params", "strs=foo&strs=bar&ints=12&ints=42&strs=baz").message)
        .isEqualTo("foo bar baz 12 42 list-params")
  }

  enum class TestEnum {
    ONE,
    TWO
  }

  class BasicParamsAction : WebAction {
    @Get("/basic-params")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(
        @QueryParam str: String,
        @QueryParam("something") other: String,
        @QueryParam int: Int,
        @QueryParam testEnum: TestEnum
    ) = Packet("${str} ${other} ${int} ${testEnum} basic-params")
  }

  class OptionalParamsAction : WebAction {
    @Get("/optional-params")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(@QueryParam str: String?, @QueryParam int: Int?)
        = Packet("${str} ${int} optional-params")
  }

  class DefaultParamsAction : WebAction {
    @Get("/default-params")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(
      @QueryParam str: String = "square",
      @QueryParam int: Int = 23,
      @QueryParam testEnum: TestEnum = TestEnum.TWO)
        = Packet("${str} ${int} ${testEnum} default-params")
  }

  class ListParamsAction : WebAction {
    @Get("/list-params")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(@QueryParam strs: List<String>, @QueryParam ints: List<Int>)
        = Packet("${strs.joinToString(separator = " ")} " +
            "${ints.joinToString(separator = " ")} list-params")
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebActionModule.create<BasicParamsAction>())
      install(WebActionModule.create<OptionalParamsAction>())
      install(WebActionModule.create<DefaultParamsAction>())
      install(WebActionModule.create<ListParamsAction>())
    }
  }

  private fun get(path: String, query: String): Packet = call(Request.Builder()
      .url(jettyService.serverUrl.newBuilder().encodedPath(path).query(query).build())
      .get())

  private fun call(request: Request.Builder): Packet {
    request.header("Accept", MediaTypes.APPLICATION_JSON)

    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request.build()).execute()
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.header("Content-Type")).isEqualTo(MediaTypes.APPLICATION_JSON)
    return packetJsonAdapter.fromJson(response.body()!!.source())!!
  }
}
