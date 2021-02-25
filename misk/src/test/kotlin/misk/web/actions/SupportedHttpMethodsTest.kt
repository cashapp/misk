package misk.web.actions

import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Delete
import misk.web.Get
import misk.web.Patch
import misk.web.Post
import misk.web.Put
import misk.web.Response
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

// Tests all supported HTTP methods.
@MiskTest(startService = true)
class SupportedHttpMethodsTest {
  @MiskTestModule
  val module = TestModule()

  val httpClient = OkHttpClient()

  @Inject private lateinit var jettyService: JettyService

  @Test
  fun get() {
    val request = Request.Builder()
      .get()
      .url(jettyService.httpServerUrl.newBuilder().encodedPath("/resources/id").build())
      .build()

    val response = httpClient.newCall(request).execute()
    assertThat(response.isSuccessful).isTrue()
    assertThat(response.body?.string()).isEqualTo("content")
  }

  @Test
  fun post() {
    val request = Request.Builder()
      .post("new resource".toRequestBody(MediaTypes.TEXT_PLAIN_UTF8_MEDIA_TYPE))
      .url(jettyService.httpServerUrl.newBuilder().encodedPath("/resources").build())
      .build()

    val response = httpClient.newCall(request).execute()
    assertThat(response.isSuccessful).isTrue()
    assertThat(response.body?.string()).isEqualTo("created")
  }

  @Test
  fun patch() {
    val request = Request.Builder()
      .patch("updated resource".toRequestBody(MediaTypes.TEXT_PLAIN_UTF8_MEDIA_TYPE))
      .url(jettyService.httpServerUrl.newBuilder().encodedPath("/resources/id").build())
      .build()

    val response = httpClient.newCall(request).execute()
    assertThat(response.isSuccessful).isTrue()
    assertThat(response.body?.string()).isEqualTo("updated")
  }

  @Test
  fun put() {
    val request = Request.Builder()
      .put("update resource".toRequestBody(MediaTypes.TEXT_PLAIN_UTF8_MEDIA_TYPE))
      .url(jettyService.httpServerUrl.newBuilder().encodedPath("/resources/id").build())
      .build()

    val response = httpClient.newCall(request).execute()
    assertThat(response.isSuccessful).isTrue()
    assertThat(response.body?.string()).isEqualTo("updated")
  }

  @Test
  fun delete() {
    val request = Request.Builder()
      .delete()
      .url(jettyService.httpServerUrl.newBuilder().encodedPath("/resources/id").build())
      .build()

    val response = httpClient.newCall(request).execute()
    assertThat(response.isSuccessful).isTrue()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.create<GetAction>())
      install(WebActionModule.create<PostAction>())
      install(WebActionModule.create<PatchAction>())
      install(WebActionModule.create<DeleteAction>())
      install(WebActionModule.create<PutAction>())
    }
  }

  internal class GetAction @Inject constructor() : WebAction {
    @Get("/resources/id")
    fun get(): String = "content"
  }

  internal class PostAction @Inject constructor() : WebAction {
    @Post("/resources")
    fun post(): String = "created"
  }

  internal class PatchAction @Inject constructor() : WebAction {
    @Patch("/resources/id")
    fun patch(): String = "updated"
  }

  internal class PutAction @Inject constructor() : WebAction {
    @Put("/resources/id")
    fun put(): String = "updated"
  }

  internal class DeleteAction @Inject constructor() : WebAction {
    @Delete("/resources/id")
    fun delete() = Response("")
  }
}
