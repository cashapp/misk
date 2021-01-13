package misk.web

import com.squareup.moshi.Moshi
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Use WebTestClient to test calls to the application at the integration level.
 *
 * To use:
 * * Add `@MiskTest(startService = true)` to the test class
 * * Add a module under test: `@MiskTestModule val module = TestModule()`
 * * Add `WebTestingModule` to the module under test: `install(WebTestingModule())`
 * * Inject `WebTestClient` in the test class
 */
class WebTestClient @Inject constructor(
  private val moshi: Moshi,
  private val jettyService: JettyService
) {
  fun get(path: String): WebTestResponse = call(path) { get() }

  /**
   * Performs a POST request with a JSON request body created from the input.
   */
  fun <T: Any> post(path: String, body: T, tClass: KClass<T>): WebTestResponse = call(path) {
    post(moshi.adapter(tClass.java).toJson(body)
      .toRequestBody(MediaTypes.APPLICATION_JSON_MEDIA_TYPE))
  }

  /**
   * Performs a POST request with a JSON request body created from the input.
   */
  inline fun <reified T: Any> post(path: String, body: T) = post(path, body, T::class)

  /**
   * Performs a POST request.
   */
  fun post(
    path: String,
    body: String,
    mediaType: MediaType = MediaTypes.APPLICATION_JSON_MEDIA_TYPE
  ) = call(path) { post(body.toRequestBody(mediaType)) }

  /**
   * Performs a call to the started service. Allows the caller to customize the action before it's
   * sent through.
   */
  fun call(path: String, action: Request.Builder.() -> Unit): WebTestResponse =
    Request.Builder()
      .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
      .apply(action)
      .let { performRequest(it) }

  private fun performRequest(request: Request.Builder): WebTestResponse {
    request.header("Accept", MediaTypes.ALL)
    val httpClient = OkHttpClient()
    return WebTestResponse(
      response = httpClient.newCall(request.build()).execute(),
      moshi = moshi
    )
  }

  data class WebTestResponse(
    val response: Response,
    private val moshi: Moshi
  ) {
    fun <T: Any> parseJson(tClass: KClass<T>): T = moshi.adapter(tClass.java)
      .fromJson(response.body!!.source())!!

    inline fun <reified T: Any> parseJson(): T = this.parseJson(T::class)
  }
}