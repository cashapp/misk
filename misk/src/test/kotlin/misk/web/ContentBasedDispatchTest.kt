package misk.web

import com.google.inject.util.Modules
import com.squareup.moshi.Moshi
import misk.MiskModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestWebModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import misk.web.mediatype.asMediaType
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class ContentBasedDispatchTest {
  @MiskTestModule
  val module = Modules.combine(
      MiskModule(),
      WebModule(),
      TestWebModule(),
      TestModule()
  )

  data class Packet(val message: String)

  private val jsonMediaType = MediaTypes.APPLICATION_JSON.asMediaType()
  private val plainTextMediaType = MediaTypes.TEXT_PLAIN_UTF8.asMediaType()
  private val weirdTextMediaType = "text/weird".asMediaType()

  private @Inject lateinit var moshi: Moshi
  private @Inject lateinit var jettyService: JettyService
  private val packetJsonAdapter get() = moshi.adapter(Packet::class.java)

  @Test
  fun postJsonExpectJson() {
    val requestContent = packetJsonAdapter.toJson(Packet("my friend"))
    val responseContent = post(jsonMediaType, requestContent, jsonMediaType).source()
    assertThat(packetJsonAdapter.fromJson(responseContent)!!.message)
        .isEqualTo("json->json my friend")
  }

  @Test
  fun postJsonExpectText() {
    val requestContent = packetJsonAdapter.toJson(Packet("my friend"))
    val responseContent = post(jsonMediaType, requestContent, plainTextMediaType).source()
    assertThat(responseContent.readUtf8()).isEqualTo("json->text my friend")
  }

  @Test
  fun postTextExpectJson() {
    val responseContent = post(plainTextMediaType, "my friend", jsonMediaType).source()
    assertThat(packetJsonAdapter.fromJson(responseContent)!!.message)
        .isEqualTo("text->json my friend")
  }

  @Test
  fun postArbitraryExpectJson() {
    val responseContent = post(weirdTextMediaType, "my friend", jsonMediaType).source()
    assertThat(packetJsonAdapter.fromJson(responseContent)!!.message)
        .isEqualTo("*->json my friend")
  }

  @Test
  fun postArbitraryExpectArbitrary() {
    val responseContent = post(weirdTextMediaType, "my friend", weirdTextMediaType).source()
    assertThat(responseContent.readUtf8()).isEqualTo("*->* my friend")
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebActionModule.create<PostJsonReturnJson>())
      install(WebActionModule.create<PostTextReturnJson>())
      install(WebActionModule.create<PostJsonReturnText>())
      install(WebActionModule.create<PostAnythingReturnJson>())
      install(WebActionModule.create<PostAnythingReturnAnything>())
    }
  }

  class PostJsonReturnJson : WebAction {
    @Post("/hello")
    @RequestContentType("application/json")
    @ResponseContentType("application/json")
    fun hello(@misk.web.RequestBody request: Packet) = Packet("json->json ${request.message}")
  }

  class PostTextReturnJson : WebAction {
    @Post("/hello")
    @RequestContentType("text/plain")
    @ResponseContentType("application/json")
    fun hello(@misk.web.RequestBody message: String) = Packet("text->json $message")
  }

  class PostJsonReturnText : WebAction {
    @Post("/hello")
    @RequestContentType("application/json")
    @ResponseContentType("text/plain")
    fun hello(@misk.web.RequestBody request: Packet) = "json->text ${request.message}"
  }

  class PostAnythingReturnJson : WebAction {
    @Post("/hello")
    @ResponseContentType("application/json")
    fun hello(@misk.web.RequestBody message: String) = Packet("*->json $message")
  }

  class PostAnythingReturnAnything : WebAction {
    @Post("/hello")
    fun hello(@misk.web.RequestBody message: String) = "*->* $message"
  }

  private fun post(
      contentType: MediaType,
      content: String,
      acceptedMediaType: MediaType? = null
  ): okhttp3.ResponseBody {
    val httpClient = OkHttpClient()
    val request = Request.Builder()
        .post(RequestBody.create(contentType, content))
        .url(jettyService.serverUrl.newBuilder().encodedPath("/hello").build())

    if (acceptedMediaType != null) {
      request.header("Accept", acceptedMediaType.toString())
    }

    val response = httpClient.newCall(request.build())
        .execute()
    assertThat(response.code()).isEqualTo(200)
    return response.body()!!
  }
}
