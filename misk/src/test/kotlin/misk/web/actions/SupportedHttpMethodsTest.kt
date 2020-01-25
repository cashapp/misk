package misk.web.actions

import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Delete
import misk.web.Get
import misk.web.Post
import misk.web.RequestBody
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID
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
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(GetAction.PATH).build())
        .build()

    val response = httpClient.newCall(request).execute()
    assertThat(response.isSuccessful).isTrue()
    assertThat(response.body?.string()).isEqualTo(GetAction.CONTENT)
  }

  @Test
  fun post() {
    val content = UUID.randomUUID().toString()
    val request = Request.Builder()
        .post(content.toRequestBody(MediaTypes.TEXT_PLAIN_UTF8_MEDIA_TYPE))
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(PostAction.PATH).build())
        .build()

    val response = httpClient.newCall(request).execute()
    assertThat(response.isSuccessful).isTrue()
    assertThat(response.body?.string()).isEqualTo(content)
  }

  @Test
  fun delete() {
    val content = UUID.randomUUID().toString()
    val request = Request.Builder()
        .delete(content.toRequestBody(MediaTypes.TEXT_PLAIN_UTF8_MEDIA_TYPE))
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(DeleteAction.PATH).build())
        .build()

    val response = httpClient.newCall(request).execute()
    assertThat(response.isSuccessful).isTrue()
    assertThat(response.body?.string()).isEqualTo(content)
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.create<GetAction>())
      install(WebActionModule.create<PostAction>())
      install(WebActionModule.create<DeleteAction>())
    }
  }

  internal class GetAction @Inject constructor() : WebAction {
    @Get(PATH)
    fun get(): String = CONTENT
    companion object {
      const val PATH = "/resources/id"
      const val CONTENT = "resource content"
    }
  }

  internal class PostAction @Inject constructor() : WebAction {
    @Post(PATH)
    fun post(@RequestBody body: String): String = body
    companion object {
      const val PATH = "/resources"
    }
  }

  internal class DeleteAction @Inject constructor() : WebAction {
    @Delete(PATH)
    fun delete(@RequestBody body: String): String = body
    companion object {
      const val PATH = GetAction.PATH
    }
  }
}
