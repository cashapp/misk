package misk.web.actions

import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import misk.web.mediatype.asMediaType
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class NotFoundActionTest {
  @MiskTestModule
  val module = TestModule()

  val httpClient = OkHttpClient()

  data class Packet(val message: String)

  private val jsonMediaType = MediaTypes.APPLICATION_JSON.asMediaType()
  private val plainTextMediaType = MediaTypes.TEXT_PLAIN_UTF8.asMediaType()
  private val weirdMediaType = "application/weird".asMediaType()

  @Inject private lateinit var moshi: Moshi
  @Inject private lateinit var jettyService: JettyService

  private val packetJsonAdapter get() = moshi.adapter(Packet::class.java)

  @Test
  fun postJsonExpectJsonPathNotFound() {
    val requestContent = packetJsonAdapter.toJson(Packet("my friend"))
    val request = post("/unknown", jsonMediaType, requestContent, jsonMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code).isEqualTo(404)
  }

  @Test
  fun postTextExpectTextPathNotFound() {
    val request = post("/unknown", plainTextMediaType, "my friend", plainTextMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code).isEqualTo(404)
  }

  @Test
  fun postArbitraryExpectArbitraryPathNotFound() {
    val request = post("/unknown", weirdMediaType, "my friend", weirdMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code).isEqualTo(404)
  }

  @Test
  fun getJsonPathNotFound() {
    val request = get("/unknown", jsonMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code).isEqualTo(404)
  }

  @Test
  fun getTextPathNotFound() {
    val request = get("/unknown", plainTextMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code).isEqualTo(404)
  }

  @Test
  fun getArbitraryPathNotFound() {
    val request = get("/unknown", weirdMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code).isEqualTo(404)
  }

  @Test
  fun headPathNotFound() {
    val request = head("/unknown", weirdMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code).isEqualTo(404)
  }
  @Test fun responseMessageSuggestsAlternativeMethod() {
    val wrongMethod = get("/echo", plainTextMediaType)
    val response = httpClient.newCall(wrongMethod).execute()
    assertThat(response.code).isEqualTo(404)
    assertThat(response.body!!.source().readUtf8()).isEqualTo("""
      |Nothing found at /echo.
      |
      |Received:
      |GET /echo
      |Accept: text/plain;charset=utf-8
      |
      |Alternative:
      |POST /echo
      |Accept: text/plain;charset=utf-8
      |Content-Type: text/plain;charset=utf-8
      |""".trimMargin())
  }

  @Test fun responseMessageSuggestsContentType() {
    val wrongContentType = post("/echo", weirdMediaType, "hello")
    val response = httpClient.newCall(wrongContentType).execute()
    assertThat(response.code).isEqualTo(404)
    assertThat(response.body!!.source().readUtf8()).isEqualTo("""
      |Nothing found at /echo.
      |
      |Received:
      |POST /echo
      |Accept: */*
      |Content-Type: application/weird; charset=utf-8
      |
      |Alternative:
      |POST /echo
      |Accept: text/plain;charset=utf-8
      |Content-Type: text/plain;charset=utf-8
      |""".trimMargin())
  }

  @Test fun responseMessageSuggestsAcceptType() {
    val wrongAccept = post("/echo", plainTextMediaType, "hello", weirdMediaType)
    val response = httpClient.newCall(wrongAccept).execute()
    assertThat(response.code).isEqualTo(404)
    assertThat(response.body!!.source().readUtf8()).isEqualTo("""
      |Nothing found at /echo.
      |
      |Received:
      |POST /echo
      |Accept: application/weird
      |Content-Type: text/plain;charset=UTF-8
      |
      |Alternative:
      |POST /echo
      |Accept: text/plain;charset=utf-8
      |Content-Type: text/plain;charset=utf-8
      |""".trimMargin())
  }

  private fun head(path: String, acceptedMediaType: MediaType? = null): Request {
    return Request.Builder()
        .head()
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
        .header("Accept", acceptedMediaType.toString())
        .build()
  }

  private fun get(path: String, acceptedMediaType: MediaType? = null): Request {
    return Request.Builder()
        .get()
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
        .header("Accept", acceptedMediaType.toString())
        .build()
  }

  private fun post(
    path: String,
    contentType: MediaType,
    content: String,
    acceptedMediaType: MediaType? = null
  ): Request {
    return Request.Builder()
        .post(content.toRequestBody(contentType))
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
        .apply {
          if (acceptedMediaType != null) {
            header("Accept", acceptedMediaType.toString())
          }
        }
        .build()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.create<EchoAction>())
      install(WebActionModule.create<EchoFooAction>())
    }
  }

  private class EchoAction @Inject constructor() : WebAction {
    @Post("/echo")
    @RequestContentType(MediaTypes.TEXT_PLAIN_UTF8)
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun echo(@RequestBody body: String): String = body
  }

  // Include another action that shared the same root path, because we want to make sure that it is
  // not accidentally suggested by the NotFoundAction.
  private class EchoFooAction @Inject constructor() : WebAction {
    @Post("/echo/foo")
    @RequestContentType(MediaTypes.TEXT_PLAIN_UTF8)
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun echo(@RequestBody body: String): String = "foo $body"
  }
}
