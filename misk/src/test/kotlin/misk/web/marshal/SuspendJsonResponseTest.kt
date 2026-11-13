package misk.web.marshal

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.WebTestClient
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Mirrors [JsonResponseTest] but all actions use `suspend fun`.
 * Verifies suspend WebActions work identically to non-suspend for all return type variants.
 *
 * Also adds [ReturnAsWrappedResponseBody] and [ReturnAsWrappedResponseBodyNoContentType]
 * which test `suspend fun(): Response<ResponseBody>` — the specific case that was previously
 * broken due to WildcardType leaking through KType.javaType for suspend function return types.
 */
@MiskTest(startService = true)
internal class SuspendJsonResponseTest {
  data class Packet(val message: String)

  @MiskTestModule val module = TestModule()

  @Inject lateinit var webTestClient: WebTestClient

  // --- Tests mirroring JsonResponseTest (suspend versions) ---

  @Test
  fun returnAsObject() {
    assertThat(get("/suspend-response/as-object").message).isEqualTo("as-object")
  }

  @Test
  fun returnAsString() {
    assertThat(get("/suspend-response/as-string").message).isEqualTo("as-string")
  }

  @Test
  fun returnAsByteString() {
    assertThat(get("/suspend-response/as-byte-string").message).isEqualTo("as-byte-string")
  }

  @Test
  fun returnAsResponseBody() {
    assertThat(get("/suspend-response/as-response-body").message).isEqualTo("as-response-body")
  }

  @Test
  fun returnAsObjectResponse() {
    assertThat(get("/suspend-response/as-wrapped-object").message).isEqualTo("as-object")
  }

  @Test
  fun returnAsStringResponse() {
    assertThat(get("/suspend-response/as-wrapped-string").message).isEqualTo("as-string")
  }

  @Test
  fun returnAsByteStringResponse() {
    assertThat(get("/suspend-response/as-wrapped-byte-string").message).isEqualTo("as-byte-string")
  }

  @Test
  fun returnAsByteStringAsResponseBodyResponse() {
    // Mirrors JsonResponseTest.ReturnAsResponseBodyResponse which returns Response<ByteString>
    assertThat(get("/suspend-response/as-wrapped-byte-string-2").message).isEqualTo("as-wrapped-byte-string-2")
  }

  // --- Tests for Response<ResponseBody> (the previously broken case) ---

  @Test
  fun returnAsWrappedResponseBody() {
    assertThat(get("/suspend-response/as-wrapped-response-body").message).isEqualTo("as-wrapped-response-body")
  }

  @Test
  fun returnAsWrappedResponseBodyWithCustomStatus() {
    val response = webTestClient.get("/suspend-response/as-wrapped-response-body-201")
    assertThat(response.response.code).isEqualTo(201)
    assertThat(response.parseJson<Packet>().message).isEqualTo("created")
  }

  @Test
  fun returnAsWrappedResponseBodyNoContentType() {
    // This is the original error case: suspend fun returning Response<ResponseBody>
    // without @ResponseContentType. Previously threw:
    //   "no marshaller for null as Response<ResponseBody>"
    val response = webTestClient.get("/suspend-response/as-wrapped-response-body-no-ct")
    assertThat(response.response.code).isEqualTo(200)
    assertThat(response.parseJson<Packet>().message).isEqualTo("no-content-type")
  }

  // --- Suspend action implementations ---

  class ReturnAsObject @Inject constructor() : WebAction {
    @Get("/suspend-response/as-object")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    suspend fun call() = Packet("as-object")
  }

  class ReturnAsString @Inject constructor() : WebAction {
    @Get("/suspend-response/as-string")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    suspend fun call() = "{\"message\":\"as-string\"}"
  }

  class ReturnAsByteString @Inject constructor() : WebAction {
    @Get("/suspend-response/as-byte-string")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    suspend fun call(): ByteString = "{\"message\":\"as-byte-string\"}".encodeUtf8()
  }

  class ReturnAsResponseBody @Inject constructor() : WebAction {
    @Get("/suspend-response/as-response-body")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    suspend fun call() = "{\"message\":\"as-response-body\"}".toResponseBody()
  }

  class ReturnAsObjectResponse @Inject constructor() : WebAction {
    @Get("/suspend-response/as-wrapped-object")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    suspend fun call() = Response(Packet("as-object"))
  }

  class ReturnAsStringResponse @Inject constructor() : WebAction {
    @Get("/suspend-response/as-wrapped-string")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    suspend fun call() = Response("{\"message\":\"as-string\"}")
  }

  class ReturnAsByteStringResponse @Inject constructor() : WebAction {
    @Get("/suspend-response/as-wrapped-byte-string")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    suspend fun call() = Response("{\"message\":\"as-byte-string\"}".encodeUtf8())
  }

  // Mirrors JsonResponseTest.ReturnAsResponseBodyResponse (which actually returns Response<ByteString>)
  class ReturnAsByteStringAsResponseBodyResponse @Inject constructor() : WebAction {
    @Get("/suspend-response/as-wrapped-byte-string-2")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    suspend fun call() = Response("""{"message":"as-wrapped-byte-string-2"}""".encodeUtf8())
  }

  // True Response<ResponseBody> — the case that was broken for suspend functions
  class ReturnAsWrappedResponseBody @Inject constructor() : WebAction {
    @Get("/suspend-response/as-wrapped-response-body")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    suspend fun call(): Response<ResponseBody> =
      Response("{\"message\":\"as-wrapped-response-body\"}".toResponseBody())
  }

  class ReturnAsWrappedResponseBodyWith201 @Inject constructor() : WebAction {
    @Get("/suspend-response/as-wrapped-response-body-201")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    suspend fun call(): Response<ResponseBody> =
      Response(body = "{\"message\":\"created\"}".toResponseBody(), statusCode = 201)
  }

  // Response<ResponseBody> without @ResponseContentType — the original error case
  class ReturnAsWrappedResponseBodyNoContentType @Inject constructor() : WebAction {
    @Get("/suspend-response/as-wrapped-response-body-no-ct")
    suspend fun call(): Response<ResponseBody> =
      Response("{\"message\":\"no-content-type\"}".toResponseBody())
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<ReturnAsObject>())
      install(WebActionModule.create<ReturnAsString>())
      install(WebActionModule.create<ReturnAsByteString>())
      install(WebActionModule.create<ReturnAsResponseBody>())
      install(WebActionModule.create<ReturnAsObjectResponse>())
      install(WebActionModule.create<ReturnAsStringResponse>())
      install(WebActionModule.create<ReturnAsByteStringResponse>())
      install(WebActionModule.create<ReturnAsByteStringAsResponseBodyResponse>())
      install(WebActionModule.create<ReturnAsWrappedResponseBody>())
      install(WebActionModule.create<ReturnAsWrappedResponseBodyWith201>())
      install(WebActionModule.create<ReturnAsWrappedResponseBodyNoContentType>())
    }
  }

  private fun get(path: String): Packet =
    webTestClient
      .get(path)
      .apply {
        assertThat(response.code).isEqualTo(200)
        assertThat(response.header("Content-Type")).isEqualTo(MediaTypes.APPLICATION_JSON)
      }
      .parseJson()
}
