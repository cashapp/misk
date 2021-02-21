package misk.web

import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class WebTestClientTest {
  @Inject lateinit var webTestClient: WebTestClient

  @MiskTestModule
  val module = TestModule()

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.create<GetAction>())
      install(WebActionModule.create<PostAction>())
    }
  }

  data class Packet(val data: String)

  class GetAction @Inject constructor() : WebAction {
    @Get("/get")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun get() = Packet("get")
  }

  class PostAction @Inject constructor() : WebAction {
    @Post("/post")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun post(@RequestBody body: Packet) = Packet(body.data)
  }

  @Test
  fun `performs a GET`() {
    assertThat(
      webTestClient
        .get("/get")
        .parseJson<Packet>()
    ).isEqualTo(Packet("get"))
  }

  @Test
  fun `performs a POST`() {
    assertThat(
      webTestClient
        .post("/post", Packet("some data"))
        .parseJson<Packet>()
    ).isEqualTo(Packet("some data"))
  }

  @Test
  fun `performs a custom call`() {
    assertThat(
      webTestClient
        .call("/post") {
          method(method = "OPTIONS", body = null)
        }
        .response.code
    ).isEqualTo(200)
  }
}
