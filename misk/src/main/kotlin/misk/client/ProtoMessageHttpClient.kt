package misk.client

import com.squareup.moshi.Moshi
import com.squareup.wire.Message
import misk.web.mediatype.MediaTypes
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * This helper class is for making HTTP requests to an endpoint that handles protobuf Message
 * classes.
 */
class ProtoMessageHttpClient constructor(
  baseUrl: String,
  private val moshi: Moshi,
  private val okHttp: OkHttpClient
) {
  private val httpUrl = baseUrl.toHttpUrlOrNull() ?: throw IllegalArgumentException(
      "could not parse $baseUrl")

  fun <O : Any> post(path: String, requestBody: Message<*, *>, responseType: Class<O>): O {
    val requestJson = moshi.adapter(requestBody.javaClass).toJson(requestBody)
    val request = Request.Builder()
        .url(httpUrl.newBuilder().encodedPath(path).build())
        .addHeader("Accept", MediaTypes.APPLICATION_JSON)
        .post(requestJson.toRequestBody(MediaTypes.APPLICATION_JSON_MEDIA_TYPE))
        .build()
    val response = okHttp.newCall(request).execute()
    if (response.code != 200) {
      throw RuntimeException(
          "request failed (${response.code} ${response.body?.string()}")
    }

    return response.body?.string()?.let { moshi.adapter(responseType).fromJson(it) }
        ?: throw IllegalStateException("could not parse response")
  }

  inline fun <reified O : Any> post(path: String, requestBody: Message<*, *>) =
      post(path, requestBody, O::class.java)
}
