package misk.web

import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import jakarta.inject.Inject
import misk.web.WebTestClientTest.Packet

@MiskTest(startService = true)
class WebTestClientTest {
  @Inject lateinit var webTestClient: WebTestClient

  @MiskTestModule
  val module = TestModule()

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<GetAction>())
      install(WebActionModule.create<GetActionWithQueryParams>())
      install(WebActionModule.create<PostAction>())
      install(WebActionModule.create<DeleteAction>())
    }
  }

  data class Packet(val data: String)

  class GetAction @Inject constructor() : WebAction {
    @Get("/get")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun get() = Packet("get")
  }

  class GetActionWithQueryParams @Inject constructor() : WebAction {
    @Get("/get/param")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun get(@QueryParam paramKey:String) = Packet("get with param value: $paramKey")
  }

  class PostAction @Inject constructor() : WebAction {
    @Post("/post")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun post(@RequestBody body: Packet) = Packet(body.data)
  }

  class DeleteAction @Inject constructor() : WebAction {
    @Delete("/delete")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun delete(@RequestBody body: Packet) = Packet("deleted ${body.data}")
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
  fun `performs a GET with query param`() {
    assertThat(
      webTestClient
        .get("/get/param?paramKey=test")
        .parseJson<Packet>()
    ).isEqualTo(Packet("get with param value: test"))
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

  @Test
  fun `performs a DELETE`() {
    assertThat(
      webTestClient
       .delete("/delete", Packet("some data"))
       .parseJson<Packet>()
    ).isEqualTo(Packet("deleted some data"))
  }
}
