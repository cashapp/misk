package misk.web.marshal

import com.squareup.moshi.Moshi
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
import okio.buffer
import okio.source
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import javax.inject.Inject

@MiskTest(startService = true)
internal class JsonRequestTest {
  data class Packet(val message: String)

  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var moshi: Moshi
  @Inject lateinit var webTestClient: WebTestClient

  @Test
  fun passAsObject() {
    assertThat(post("/as-object", Packet("foo")).message).isEqualTo("foo as-object")
  }

  @Test
  fun passAsString() {
    assertThat(post("/as-string", Packet("foo")).message).isEqualTo("foo as-string")
  }

  @Test
  fun passAsByteString() {
    assertThat(post("/as-byte-string", Packet("foo")).message).isEqualTo("foo as-byte-string")
  }

  class PassAsObject @Inject constructor() : WebAction {
    @Post("/as-object")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(@RequestBody packet: Packet) = Packet("${packet.message} as-object")
  }

  class PassAsString @Inject constructor() : WebAction {
    @Inject lateinit var moshi: Moshi
    private val packetJsonAdapter get() = moshi.adapter(Packet::class.java)

    @Post("/as-string")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(@RequestBody encodedPacket: String): Packet {
      val incomingPacket = packetJsonAdapter.fromJson(encodedPacket)
      return Packet("${incomingPacket?.message} as-string")
    }
  }

  class PassAsByteString @Inject constructor() : WebAction {
    @Inject lateinit var moshi: Moshi
    private val packetJsonAdapter get() = moshi.adapter(Packet::class.java)

    @Post("/as-byte-string")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(@RequestBody encodedPacket: ByteString): Packet {
      val source = ByteArrayInputStream(encodedPacket.toByteArray()).source().buffer()
      val incomingPacket = packetJsonAdapter.fromJson(source)
      return Packet("${incomingPacket?.message} as-byte-string")
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.create<PassAsObject>())
      install(WebActionModule.create<PassAsString>())
      install(WebActionModule.create<PassAsByteString>())
    }
  }

  private fun post(path: String, packet: Packet): Packet = webTestClient.post(path, packet)
    .apply {
      assertThat(response.code).isEqualTo(200)
      assertThat(response.header("Content-Type")).isEqualTo(MediaTypes.APPLICATION_JSON)
    }.parseJson()
}
