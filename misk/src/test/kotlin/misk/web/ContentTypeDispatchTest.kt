package misk.web

import com.google.inject.util.Modules
import com.squareup.moshi.Moshi
import misk.MiskModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestWebModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.marshal.GenericUnmarshallers
import misk.web.marshal.Unmarshaller
import misk.web.marshal.UnmarshallerModule
import misk.web.mediatype.MediaTypes
import misk.web.mediatype.asMediaType
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import kotlin.reflect.KType

private val jsonMediaType = MediaTypes.APPLICATION_JSON.asMediaType()
private val plainTextMediaType = MediaTypes.TEXT_PLAIN_UTF8.asMediaType()
private val complexMediaType = "text/complicated".asMediaType()


@MiskTest(startService = true)
internal class ContentTypeDispatchTest {
  @MiskTestModule
  val module = Modules.combine(
      MiskModule(),
      WebModule(),
      TestWebModule(),
      TestModule()
  )

  data class ComplexPacket(
      val message: String,
      val composedBy: String = "default"
  ) {
    companion object {
      @JvmStatic
      fun valueOf(value: String): ComplexPacket = ComplexPacket(value, "valueOf")
    }
  }

  @Inject lateinit var moshi: Moshi
  @Inject lateinit var jettyService: JettyService
  private val complexPacketJsonAdapter get() = moshi.adapter(ComplexPacket::class.java)

  @Test
  fun jsonContentTypeUnmarshalledWithJson() {
    val requestContent = complexPacketJsonAdapter.toJson(ComplexPacket("my friend", "json"))
    val responseContent = post(
        jsonMediaType,
        requestContent,
        jsonMediaType,
        "/complex"
    ).source()
    assertThat(complexPacketJsonAdapter.fromJson(responseContent)!!.message)
        .isEqualTo("json or complicated or text->json my friend json")
  }

  @Test
  fun complexContentTypeUnmarshalledWithComplex() {
    val responseContent = post(
        complexMediaType,
        "my friend",
        jsonMediaType,
        "/complex"
    ).source()
    assertThat(complexPacketJsonAdapter.fromJson(responseContent)!!.message)
        .isEqualTo("json or complicated or text->json my friend unmarshaller")
  }

  @Test
  fun textContentTypeUnmarshalledWithGeneric() {
    val responseContent = post(
        plainTextMediaType,
        "my friend",
        jsonMediaType,
        "/complex"
    ).source()
    assertThat(complexPacketJsonAdapter.fromJson(responseContent)!!.message)
        .isEqualTo("json or complicated or text->json my friend valueOf")
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebActionModule.create<PostMultiReturnJson>())

      install(UnmarshallerModule.create<ComplexPacketUnmarshaller.Factory>())
    }
  }

  class PostMultiReturnJson : WebAction {
    @Post("/complex")
    @RequestContentType("application/json", "text/complicated", "text/plain")
    @ResponseContentType("application/json")
    fun hello(@misk.web.RequestBody request: ComplexPacket) = ComplexPacket(
        "json or complicated or text->" +
            "json ${request.message} ${request.composedBy}"
    )
  }

  private fun post(
      contentType: MediaType,
      content: String,
      acceptedMediaType: MediaType? = null,
      path: String = "/hello"
  ): okhttp3.ResponseBody {
    val httpClient = OkHttpClient()
    val request = Request.Builder()
        .post(RequestBody.create(contentType, content))
        .url(jettyService.serverUrl.newBuilder().encodedPath(path).build())

    if (acceptedMediaType != null) {
      request.header("Accept", acceptedMediaType.toString())
    }

    val response = httpClient.newCall(request.build()).execute()
    assertThat(response.code()).isEqualTo(200)
    return response.body()!!
  }

  class ComplexPacketUnmarshaller : Unmarshaller {
    override fun unmarshal(source: BufferedSource): Any? = ComplexPacket(
        source.readUtf8(), "unmarshaller"
    )

    class Factory : Unmarshaller.Factory {
      override fun create(mediaType: MediaType, type: KType): Unmarshaller? {
        if (mediaType.type() != complexMediaType.type() ||
            mediaType.subtype() != complexMediaType.subtype()) return null

        if (GenericUnmarshallers.canHandle(type)) return null
        return ComplexPacketUnmarshaller()
      }
    }
  }
}
