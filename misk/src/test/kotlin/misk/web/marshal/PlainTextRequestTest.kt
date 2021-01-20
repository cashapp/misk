package misk.web.marshal

import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebTestClient
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import okio.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
internal class PlainTextRequestTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var webTestClient: WebTestClient

  @Test
  fun passAsString() {
    assertThat(post("/as-string", "foo")).isEqualTo("foo as-string")
  }

  @Test
  fun passAsByteString() {
    assertThat(post("/as-byte-string", "foo")).isEqualTo("foo as-byte-string")
  }

  class PassAsString @Inject constructor() : WebAction {
    @Post("/as-string")
    @RequestContentType(MediaTypes.TEXT_PLAIN_UTF8)
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call(@RequestBody message: String): String = "$message as-string"
  }

  class PassAsByteString @Inject constructor() : WebAction {
    @Post("/as-byte-string")
    @RequestContentType(MediaTypes.TEXT_PLAIN_UTF8)
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call(@RequestBody messageBytes: ByteString): String =
      "${messageBytes.utf8()} as-byte-string"
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.create<PassAsString>())
      install(WebActionModule.create<PassAsByteString>())
    }
  }

  private fun post(path: String, message: String): String = webTestClient
    .post(path, message, MediaTypes.TEXT_PLAIN_UTF8_MEDIA_TYPE)
    .apply {
      assertThat(response.code).isEqualTo(200)
      assertThat(response.header("Content-Type")).isEqualTo(MediaTypes.TEXT_PLAIN_UTF8)
    }.response.body?.string()!!
}
