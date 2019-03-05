package misk.web.actions

import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebActionModule
import misk.web.WebTestingModule
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
    assertThat(response.code()).isEqualTo(404)
  }

  @Test
  fun postTextExpectTextPathNotFound() {
    val request = post("/unknown", plainTextMediaType, "my friend", plainTextMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(404)
  }

  @Test
  fun postArbitraryExpectArbitraryPathNotFound() {
    val request = post("/unknown", weirdMediaType, "my friend", weirdMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(404)
  }

  @Test
  fun getJsonPathNotFound() {
    val request = get("/unknown", jsonMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(404)
  }

  @Test
  fun getTextPathNotFound() {
    val request = get("/unknown", plainTextMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(404)
  }

  @Test
  fun getArbitraryPathNotFound() {
    val request = get("/unknown", weirdMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(404)
  }

  @Test
  fun headPathNotFound() {
    val request = head("/unknown", weirdMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(404)
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
        .post(RequestBody.create(contentType, content))
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
        .header("Accept", acceptedMediaType.toString())
        .build()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.forAction<NotFoundAction>())
    }
  }
}
