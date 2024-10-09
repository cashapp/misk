package misk.web.marshal

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.WebTestClient
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers
import okhttp3.MediaType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType

@MiskTest(startService = true)
class CustomMarshallerTest {
  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var webTestClient: WebTestClient

  @Test
  fun `it writes both headers and body`() {
    val response = webTestClient.get("/custom-object").response
    assertThat(response.code).isEqualTo(200)
    val foo = response.headers["foo"]
    val bar = response.headers["bar"]
    assertThat(foo).matches("^\\d+$")
    assertThat(bar).matches("^\\d+$")
    assertThat(response.body!!.string()).isEqualTo("Foo: $foo\nBar: $bar")
  }

  class CustomObject(val foo: String, val bar: String)

  class CustomObjectMarshaller @Inject constructor(
    private val mediaType: MediaType,
  ) : Marshaller<Any> {
    override fun contentType() = mediaType
    override fun responseBody(o: Any, responseHeaders: Headers.Builder): ResponseBody {
      o as CustomObject
      responseHeaders["foo"] = o.foo
      responseHeaders["bar"] = o.bar
      return "Foo: ${o.foo}\nBar: ${o.bar}".toResponseBody()
    }

    class Factory @Inject constructor() : Marshaller.Factory {
      override fun create(mediaType: MediaType, type: KType): Marshaller<Any>? =
        if (type.isSubtypeOf(CustomObject::class.starProjectedType))
          CustomObjectMarshaller(mediaType) else null
    }
  }

  class TestRoutes : WebAction {
    @Get("/custom-object")
    @ResponseContentType(MediaTypes.TEXT_HTML)
    fun call() = CustomObject(
      Random().nextInt().toString(),
      Random().nextInt().toString(),
    )
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())

      multibind<Marshaller.Factory>().to<CustomObjectMarshaller.Factory>()

      install(WebActionModule.create<TestRoutes>())
    }
  }
}
