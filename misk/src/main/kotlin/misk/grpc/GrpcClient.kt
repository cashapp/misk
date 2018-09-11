package misk.grpc

import misk.web.mediatype.MediaTypes
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

// TODO(jwilson): Replace this awkward beast with a Retrofit2 interface.
class GrpcClient(
  val client: OkHttpClient,
  val url: HttpUrl
) {
  fun <S, R> call(method: GrpcMethod<S, R>, request: S): R? {
    val requestBody = object : RequestBody() {
      override fun contentType() = MediaTypes.APPLICATION_GRPC_MEDIA_TYPE

      override fun writeTo(sink: BufferedSink) {
        GrpcWriter.get(sink, method.requestAdapter).use {
          it.writeMessage(request)
        }
      }
    }

    val httpRequest = Request.Builder()
        .url(url.resolve(method.path)!!)
        .addHeader("grpc-trace-bin", "")
        .addHeader("grpc-accept-encoding", "gzip")
        .post(requestBody)
        .build()
    val call = client.newCall(httpRequest)
    val response = call.execute()
    val grpcEncoding = response.header("grpc-encoding")
    val responseSource = response.body()!!.source()

    GrpcReader.get(responseSource, method.responseAdapter, grpcEncoding).use {
      return it.readMessage()
    }
  }
}