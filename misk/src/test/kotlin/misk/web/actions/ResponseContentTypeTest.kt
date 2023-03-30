package misk.web.actions

import com.squareup.protos.test.grpc.HelloReply
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.PathParam
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest(startService = true)
class ResponseContentTypeTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var jettyService: JettyService

  private val httpClient = OkHttpClient()

  @Test
  fun `the Accept header can control what response type is returned if the endpoint supports it`() {
    val requestForJson = get("/hello/jeff", MediaTypes.APPLICATION_JSON_MEDIA_TYPE)
    val jsonResponse = httpClient.newCall(requestForJson).execute()
    assertThat(jsonResponse.code).isEqualTo(200)
    assertThat(jsonResponse.body!!.string()).isEqualTo("""{"message":"howdy, jeff"}""")

    val requestForProto = get("/hello/jeff", MediaTypes.APPLICATION_PROTOBUF_MEDIA_TYPE)
    val protoResponse = httpClient.newCall(requestForProto).execute()
    assertThat(protoResponse.code).isEqualTo(200)
    assertThat(protoResponse.body!!.string())
      .isEqualTo(HelloReply("howdy, jeff").encodeByteString().string(Charset.defaultCharset()))
  }

  @Test
  fun `the Accept header does not allow unsupported types to be returned`() {
    // Make sure if we don't specify an Accept header, that it defaults to JSON
    val headerlessRequest = get("/hello-json/jeff")
    val headerlessResponse = httpClient.newCall(headerlessRequest).execute()
    assertThat(headerlessResponse.code).isEqualTo(200)
    assertThat(headerlessResponse.body!!.string()).isEqualTo("""{"message":"howdy, jeff"}""")

    val requestForJson = get("/hello-json/jeff", MediaTypes.APPLICATION_JSON_MEDIA_TYPE)
    val jsonResponse = httpClient.newCall(requestForJson).execute()
    assertThat(jsonResponse.code).isEqualTo(200)
    assertThat(jsonResponse.body!!.string()).isEqualTo("""{"message":"howdy, jeff"}""")

    val requestForProto = get("/hello-json/jeff", MediaTypes.APPLICATION_PROTOBUF_MEDIA_TYPE)
    val protoResponse = httpClient.newCall(requestForProto).execute()
    assertThat(protoResponse.code).isEqualTo(415)
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<HelloAction>())
    }
  }

  @Singleton
  class HelloAction @Inject constructor() : WebAction {
    @Get("/hello/{name}")
    @ResponseContentType(
      MediaTypes.APPLICATION_JSON,
      MediaTypes.APPLICATION_PROTOBUF,
    )
    fun sayHello(@PathParam("name") name: String): HelloReply {
      return HelloReply.Builder()
        .message("howdy, $name")
        .build()
    }

    @Get("/hello-json/{name}")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun sayHelloJson(@PathParam("name") name: String): HelloReply {
      return HelloReply.Builder()
        .message("howdy, $name")
        .build()
    }
  }

  private fun get(path: String, acceptedMediaType: MediaType? = null): Request {
    return Request.Builder()
      .get()
      .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
      .header("Accept", acceptedMediaType.toString())
      .build()
  }
}
