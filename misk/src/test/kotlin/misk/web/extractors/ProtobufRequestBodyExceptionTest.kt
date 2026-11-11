package misk.web.extractors

import com.squareup.protos.test.parsing.RequiredFieldMessage
import jakarta.inject.Inject
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_OK
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
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class ProtobufRequestBodyExceptionTest {

  @MiskTestModule val module = TestModule()

  @Inject lateinit var webTestClient: WebTestClient

  @Test
  fun `protobuf body missing a required field returns 400 Bad Request`() {
    postProtobuf("/echo", ByteString.EMPTY).use { assertThat(it.code).isEqualTo(HTTP_BAD_REQUEST) }
  }

  @Test
  fun `valid protobuf body returns 200 OK`() {
    postProtobuf("/echo", RequiredFieldMessage("dino").encodeByteString()).use {
      assertThat(it.code).isEqualTo(HTTP_OK)
      assertThat(RequiredFieldMessage.ADAPTER.decode(it.body.source()).name).isEqualTo("dino")
    }
  }

  @Test
  fun `IllegalStateException thrown by the action still returns 500`() {
    postProtobuf("/throw", RequiredFieldMessage("dino").encodeByteString()).use {
      assertThat(it.code).isEqualTo(HTTP_INTERNAL_ERROR)
    }
  }

  private fun postProtobuf(path: String, body: ByteString): Response =
    webTestClient.call(path) { post(body.toRequestBody(MediaTypes.APPLICATION_PROTOBUF_MEDIA_TYPE)) }.response

  class RequiredFieldAction @Inject constructor() : WebAction {
    @Post("/echo")
    @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
    @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
    fun echo(@RequestBody request: RequiredFieldMessage): RequiredFieldMessage = request

    @Post("/throw")
    @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
    @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
    fun throwing(@RequestBody request: RequiredFieldMessage): RequiredFieldMessage =
      throw IllegalStateException("action failed")
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.create<RequiredFieldAction>())
    }
  }
}
