package misk.web.extractors

import com.squareup.protos.test.parsing.RequiredFieldMessage
import jakarta.inject.Inject
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class ProtobufRequestBodyExceptionTest {

  @MiskTestModule val module = TestModule()

  @Inject lateinit var jettyService: JettyService

  @Test
  fun `protobuf body missing a required field returns 400 Bad Request`() {
    val response = postProtobuf("/echo", ByteString.EMPTY)
    assertThat(response.code).isEqualTo(HTTP_BAD_REQUEST)
  }

  @Test
  fun `valid protobuf body returns 200 OK`() {
    val response = postProtobuf("/echo", RequiredFieldMessage("dino").encodeByteString())
    assertThat(response.code).isEqualTo(200)
    assertThat(RequiredFieldMessage.ADAPTER.decode(response.body.bytes()).name).isEqualTo("dino")
  }

  @Test
  fun `IllegalStateException thrown by the action still returns 500`() {
    val response = postProtobuf("/throw", RequiredFieldMessage("dino").encodeByteString())
    assertThat(response.code).isEqualTo(HTTP_INTERNAL_ERROR)
  }

  private fun postProtobuf(path: String, body: ByteString): okhttp3.Response {
    val request =
      Request.Builder()
        .post(body.toRequestBody(MediaTypes.APPLICATION_PROTOBUF.toMediaType()))
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
        .build()
    return OkHttpClient().newCall(request).execute()
  }

  class EchoAction @Inject constructor() : WebAction {
    @Post("/echo")
    @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
    @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
    fun echo(@RequestBody request: RequiredFieldMessage): RequiredFieldMessage = request
  }

  class ThrowingAction @Inject constructor() : WebAction {
    @Post("/throw")
    @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
    @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
    fun throwing(@RequestBody request: RequiredFieldMessage): RequiredFieldMessage =
      throw IllegalStateException("action failed")
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<EchoAction>())
      install(WebActionModule.create<ThrowingAction>())
    }
  }
}
