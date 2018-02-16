package misk.client

import com.google.inject.util.Modules
import helpers.protos.Dinosaur
import misk.MiskModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestWebModule
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class ProtoMessageHttpClientTest {
 @MiskTestModule
 val module = Modules.combine(
   MiskModule(),
   WebModule(),
   TestWebModule(),
   TestModule()
 )

 private @Inject lateinit var jettyService: JettyService
 private @Inject lateinit var httpClient: ProtoMessageHttpClient

 @Test
 fun protoMessageHttpCall() {
  val dinoMessage = Dinosaur.Builder()
    .name("stegosaurus")
    .picture_urls(listOf(
      "https://cdn.dinopics.com/stego.jpg",
      "https://cdn.dinopics.com/stego2.png"
    ))
    .build()

  val response = httpClient.post<Dinosaur>(
    jettyService.serverUrl.toString(), "/cooldinos", dinoMessage)
  assertThat(response.name).isEqualTo("supersaurus")
  assertThat(response.picture_urls).isEqualTo(listOf(
    "https://cdn.dinopics.com/stego.jpg",
    "https://cdn.dinopics.com/stego2.png"
  ))
 }

 class ReturnADinosaur : WebAction {
  @Post("/cooldinos")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun addHashedAliases(
    @RequestBody requestBody: Dinosaur
  ): Dinosaur = requestBody.newBuilder().name("supersaurus").build()
 }

 class TestModule : KAbstractModule() {
  override fun configure() {
   install(WebActionModule.create<ReturnADinosaur>())
  }
 }
}
