package misk.grpc

import misk.web.mediatype.MediaTypes
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.Internal

// TODO(jwilson): Replace this awkward beast with a Retrofit2 interface.
class GrpcClient(
  val client: OkHttpClient,
  val url: HttpUrl
) {
  fun <S, R> call(method: GrpcMethod<S, R>, request: S): List<R> {
    val requestBuilder = Request.Builder()
    requestBuilder
        .url(url.resolve(method.path)!!)
        .addHeader("content-type", MediaTypes.APPLICATION_GRPC_MEDIA_TYPE.toString())
        .addHeader("te", "trailers")
        .addHeader("grpc-trace-bin", "")
        .addHeader("grpc-accept-encoding", "gzip")

    Internal.instance.duplex(requestBuilder, "POST")

    val httpRequest = requestBuilder.build()
    val call = client.newCall(httpRequest)
    val response = call.execute()

    val sink = Internal.instance.sink(response)
    val grpcWriter = GrpcWriter.get(sink, method.requestAdapter)
    grpcWriter.writeMessage(request)
    grpcWriter.flush()
    grpcWriter.close()

    val grpcEncoding = response.header("grpc-encoding")
    val responseSource = response.body()!!.source()

    val result = mutableListOf<R>()

    GrpcReader.get(responseSource, method.responseAdapter, grpcEncoding).use {
      while (true) {
        val message = it.readMessage() ?: break
        println("RECEIVED $message")
        result += message
      }
    }

    println("Trailers: ${response.trailers()}")

    return result.toList()
  }
}