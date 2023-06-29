package misk.web

import com.squareup.moshi.Moshi
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import misk.web.mediatype.asMediaType
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class ContentBasedDispatchTest {
  @MiskTestModule
  val module = TestModule()

  val httpClient = OkHttpClient()

  data class Packet(val message: String)

  private val jsonMediaType = MediaTypes.APPLICATION_JSON.asMediaType()
  private val plainTextMediaType = MediaTypes.TEXT_PLAIN_UTF8.asMediaType()
  private val grpcMediaType = MediaTypes.APPLICATION_GRPC.asMediaType()
  private val weirdTextMediaType = "text/weird".asMediaType()
  private val weirdMediaType = "weird/weird".asMediaType()

  @Inject private lateinit var moshi: Moshi
  @Inject private lateinit var jettyService: JettyService

  private val packetJsonAdapter get() = moshi.adapter(Packet::class.java)

  @Test
  fun postJsonExpectJson() {
    val requestContent = packetJsonAdapter.toJson(Packet("my friend"))
    val responseContent = postHello(jsonMediaType, requestContent, jsonMediaType).source()
    assertThat(packetJsonAdapter.fromJson(responseContent)!!.message)
      .isEqualTo("json->json my friend")
  }

  @Test
  fun postJsonExpectPlainText() {
    val requestContent = packetJsonAdapter.toJson(Packet("my friend"))
    val responseContent = postHello(jsonMediaType, requestContent, plainTextMediaType).source()
    assertThat(responseContent.readUtf8()).isEqualTo("json->text my friend")
  }

  @Test
  fun postPlainTextExpectJson() {
    val responseContent = postHello(plainTextMediaType, "my friend", jsonMediaType).source()
    assertThat(packetJsonAdapter.fromJson(responseContent)!!.message)
      .isEqualTo("text->json my friend")
  }

  @Test
  fun postPlainTextExpectPlainText() {
    val responseContent = postHello(plainTextMediaType, "my friend", plainTextMediaType).source()
    assertThat(responseContent.readUtf8()).isEqualTo("text->text my friend")
  }

  @Test
  fun postWeirdTextExpectPlainText() {
    val responseContent = postHello(weirdTextMediaType, "my friend", plainTextMediaType).source()
    assertThat(responseContent.readUtf8()).isEqualTo("text*->text my friend")
  }

  @Test
  fun postPlainTextExpectWeirdText() {
    val responseContent = postHello(plainTextMediaType, "my friend", weirdTextMediaType).source()
    assertThat(responseContent.readUtf8()).isEqualTo("text->text* my friend")
  }

  @Test
  fun postArbitraryExpectJson() {
    val responseContent = postHello(weirdMediaType, "my friend", jsonMediaType).source()
    assertThat(packetJsonAdapter.fromJson(responseContent)!!.message)
      .isEqualTo("*->json my friend")
  }

  @Test
  fun postArbitraryExpectArbitrary() {
    val responseContent = postHello(
      weirdMediaType,
      "my friend",
      weirdMediaType
    ).source()
    assertThat(responseContent.readUtf8()).isEqualTo("*->* my friend")
  }

  @Test
  fun postGrpcExpectResponse() {
    val body = "Test".toByteArray().toByteString().toRequestBody()
    val request = newRequest("/hello-bytes", grpcMediaType, body)
    val response = httpClient.newCall(request)
      .execute()
    assertThat(response.code).isEqualTo(200)
    val responseContent = response.body!!.source()
    val strResp = String(responseContent.readByteString().toByteArray())
    assertThat(strResp).isEqualTo("Test")
    val trailers = response.trailers()
    assertThat(trailers.size).isEqualTo(1)
    assertThat(trailers["grpc-status"]).isEqualTo("0")
  }

  @Test
  fun postGrpcExpect404Response() {
    val request = newRequest(
      "/doesnt-exist",
      grpcMediaType,
      "Test".toByteArray().toByteString().toRequestBody(),
      grpcMediaType.toString()
    )
    val response = httpClient.newCall(request)
      .execute()
    assertThat(response.code).isEqualTo(404)
    assertThat(response.message).isEqualTo("Not Found")
    assertThat(response.headers["Content-Type"]).isEqualTo("text/plain;charset=utf-8")
    assertThat(response.body!!.string()).startsWith("Nothing found at")
  }

  @Test
  fun doesntThrowOnInvalidMediaType() {
    val request = newRequest("/hello", plainTextMediaType, "hello".toRequestBody(), "Totally Invalid Media Type")
    val response = httpClient.newCall(request).execute()
    assertThat(response.code).isEqualTo(200)
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<PostPlainTextReturnAnyText>())
      install(WebActionModule.create<PostAnyTextReturnPlainText>())
      install(WebActionModule.create<PostPlainTextReturnPlainText>())
      install(WebActionModule.create<PostJsonReturnJson>())
      install(WebActionModule.create<PostPlainTextReturnJson>())
      install(WebActionModule.create<PostJsonReturnPlainText>())
      install(WebActionModule.create<PostAnythingReturnJson>())
      install(WebActionModule.create<PostAnythingReturnAnything>())
      install(WebActionModule.create<PostGrpcReturnResponseBody>())
    }
  }

  class PostJsonReturnJson @Inject constructor() : WebAction {
    @Post("/hello")
    @RequestContentType("application/json")
    @ResponseContentType("application/json")
    fun hello(@misk.web.RequestBody request: Packet) = Packet("json->json ${request.message}")
  }

  class PostPlainTextReturnJson @Inject constructor() : WebAction {
    @Post("/hello")
    @RequestContentType("text/plain")
    @ResponseContentType("application/json")
    fun hello(@misk.web.RequestBody message: String) = Packet("text->json $message")
  }

  class PostPlainTextReturnPlainText @Inject constructor() : WebAction {
    @Post("/hello")
    @RequestContentType("text/plain")
    @ResponseContentType("text/plain")
    fun hello(@misk.web.RequestBody message: String) = "text->text $message"
  }

  class PostAnyTextReturnPlainText @Inject constructor() : WebAction {
    @Post("/hello")
    @RequestContentType("text/*")
    @ResponseContentType("text/plain")
    fun hello(@misk.web.RequestBody message: String) = "text*->text $message"
  }

  class PostPlainTextReturnAnyText @Inject constructor() : WebAction {
    @Post("/hello")
    @RequestContentType("text/plain")
    @ResponseContentType("text/*")
    fun hello(@misk.web.RequestBody message: String) = "text->text* $message"
  }

  class PostJsonReturnPlainText @Inject constructor() : WebAction {
    @Post("/hello")
    @RequestContentType("application/json")
    @ResponseContentType("text/plain")
    fun hello(@misk.web.RequestBody request: Packet) = "json->text ${request.message}"
  }

  class PostAnythingReturnJson @Inject constructor() : WebAction {
    @Post("/hello")
    @ResponseContentType("application/json")
    fun hello(@misk.web.RequestBody message: String) = Packet("*->json $message")
  }

  class PostAnythingReturnAnything @Inject constructor() : WebAction {
    @Post("/hello")
    fun hello(@misk.web.RequestBody message: String) = "*->* $message"
  }

  class PostGrpcReturnResponseBody @Inject constructor() : WebAction {
    @Grpc("/hello-bytes")
    fun hello(@misk.web.RequestBody message: ByteString): Response<ResponseBody> {
      return Response(message.toResponseBody(), trailers = { Headers.headersOf("grpc-status", "0") })
    }
  }

  private fun postHello(
    contentType: MediaType,
    content: String?,
    acceptedMediaType: MediaType? = null
  ): okhttp3.ResponseBody {
    val request = newRequest("/hello", contentType, content!!.toRequestBody(contentType), acceptedMediaType.toString())
    val response = httpClient.newCall(request)
      .execute()
    assertThat(response.code).isEqualTo(200)
    return response.body!!
  }

  private fun newRequest(
    path: String,
    contentType: MediaType,
    requestBody: RequestBody,
    acceptedMediaType: String? = null
  ): Request {
    val request = Request.Builder()
      .post(requestBody)
      .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())

    if (acceptedMediaType != null) {
      request.header("Accept", acceptedMediaType.toString())
    }

    request.header("Content-Type", contentType.toString())

    return request.build()
  }
}
