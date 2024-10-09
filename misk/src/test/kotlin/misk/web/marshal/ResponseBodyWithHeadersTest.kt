package misk.web.marshal

import jakarta.inject.Inject
import misk.Action
import misk.ApplicationInterceptor
import misk.Chain
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
import kotlin.math.absoluteValue
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType

@MiskTest(startService = true)
class ResponseBodyWithHeadersTest {
  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var webTestClient: WebTestClient

  @Test
  fun `it writes response headers and body`() {
    val response = webTestClient.get("/custom-object").response
    assertThat(response.code).isEqualTo(200)
    val foo = response.headers["foo"]
    assertThat(foo).matches("^\\d+$")
    assertThat(response.body!!.string()).isEqualTo("Foo: $foo")
  }

  @Test
  fun `it can modify response headers`() {
    val response = webTestClient.get("/custom-object").response
    assertThat(response.code).isEqualTo(200)
    assertThat(response.headers["tobemodified"]).matches("^\\d+-modified$")
  }

  class CustomObject(val foo: String)

  class CustomObjectMarshaller @Inject constructor(
    private val mediaType: MediaType,
  ) : Marshaller<Any> {
    override fun contentType() = mediaType
    override fun responseBody(o: Any) = TODO("not implemented")
    override fun responseBody(o: Any, responseHeaders: Headers.Builder): ResponseBody {
      responseHeaders.set("tobemodified", responseHeaders.get("tobemodified") + "-modified")
      with(o as CustomObject) {
        responseHeaders["foo"] = foo
        return "Foo: $foo".toResponseBody()
      }
    }

    class Factory @Inject constructor() : Marshaller.Factory {
      override fun create(mediaType: MediaType, type: KType): Marshaller<Any>? =
        if (type.isSubtypeOf(CustomObject::class.starProjectedType))
          CustomObjectMarshaller(mediaType) else null
    }
  }

  class HeaderAddingInterceptor @Inject constructor() : ApplicationInterceptor {
    override fun intercept(chain: Chain): Any {
      chain.httpCall.setResponseHeader("tobemodified", randomInt())
      return chain.proceed(chain.args)
    }

    class Factory @Inject constructor() : ApplicationInterceptor.Factory {
      override fun create(action: Action): ApplicationInterceptor = HeaderAddingInterceptor()
    }
  }

  class TestRoutes @Inject constructor() : WebAction {
    @Get("/custom-object")
    @ResponseContentType(MediaTypes.TEXT_HTML)
    fun call() = CustomObject(randomInt())
  }

  companion object {
    private fun randomInt() = Random().nextInt().absoluteValue.toString()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())

      multibind<ApplicationInterceptor.Factory>().to<HeaderAddingInterceptor.Factory>()
      multibind<Marshaller.Factory>().to<CustomObjectMarshaller.Factory>()

      install(WebActionModule.create<TestRoutes>())
    }
  }
}
