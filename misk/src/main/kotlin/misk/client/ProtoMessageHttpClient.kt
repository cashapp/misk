package misk.client

import com.squareup.moshi.Moshi
import com.squareup.wire.Message
import misk.web.mediatype.MediaTypes
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import javax.inject.Inject

/**
 * This helper class is for making HTTP requests to an endpoint that handles protobuf Message
 * classes.
 */
class ProtoMessageHttpClient @Inject constructor(var moshi: Moshi) {

  private val okHttp = OkHttpClient()

  fun <O : Any> post(
      baseUrl: String,
      path: String,
      requestBody: Message<*, *>,
      responseType: Class<O>
  ): O {
    val httpUrl = HttpUrl.parse(baseUrl) ?:
        throw IllegalArgumentException("could not parse $baseUrl")

    val requestJson = moshi.adapter(requestBody.javaClass).toJson(requestBody)
    val request = Request.Builder()
        .url(httpUrl.newBuilder().encodedPath(path).build())
        .addHeader("Accept", MediaTypes.APPLICATION_JSON)
        .post(RequestBody.create(MediaTypes.APPLICATION_JSON_MEDIA_TYPE, requestJson))
        .build()
    val response = okHttp.newCall(request).execute()
    if (response.code() != 200) {
      throw RuntimeException("request failed (${response.code()} ${response.body()?.string()}")
    }

    return response.body()?.string()?.let { moshi.adapter(responseType).fromJson(it) }
        ?: throw IllegalStateException("could not parse response")
  }

  inline fun <reified O : Any> post(address:String, path: String, requestBody: Message<*, *>) =
      post(address, path, requestBody, O::class.java)
}
