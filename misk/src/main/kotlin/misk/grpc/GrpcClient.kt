package misk.grpc

import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.launch
import misk.web.mediatype.MediaTypes.APPLICATION_GRPC_MEDIA_TYPE
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.duplex.DuplexRequestBody
import java.io.IOException

// TODO(jwilson): Replace this awkward beast with a Retrofit2 interface.
class GrpcClient(
  val client: OkHttpClient,
  val url: HttpUrl
) {
  suspend fun <S, R> call(method: GrpcMethod<S, R>, request: S): List<R> {
    val responseChannel = Channel<R>(1)

    val requestChannel = call(method, responseChannel as SendChannel<R>)
    requestChannel.send(request)
    requestChannel.close()

    val result = mutableListOf<R>()
    for (r in responseChannel) {
      result += r
    }
    return result
  }

  suspend fun <S, R> call(method: GrpcMethod<S, R>, responseChannel: SendChannel<R>): SendChannel<S> {
    val duplexRequestBody = DuplexRequestBody(APPLICATION_GRPC_MEDIA_TYPE, 1024 * 1024)

    val httpRequest = Request.Builder()
        .url(url.resolve(method.path)!!)
        .addHeader("te", "trailers")
        .addHeader("grpc-trace-bin", "")
        .addHeader("grpc-accept-encoding", "gzip")
        .method("POST", duplexRequestBody)
        .build()

    val call = client.newCall(httpRequest)
    val requestChannel= Channel<S>(0)
    val sink = duplexRequestBody.createSink(call)
    val response = duplexRequestBody.awaitExecute()

    // Write the request channel.
    launch {
      val requestWriter = GrpcWriter.get(sink, method.requestAdapter)
      requestWriter.use {
        for (message in requestChannel) {
          requestWriter.writeMessage(message)
          requestWriter.flush()
        }
      }
    }


    // Read the response channel.
    launch {
      val grpcEncoding = response.header("grpc-encoding")
      val responseSource = response.body()!!.source()
      val responseReader = GrpcReader.get(responseSource, method.responseAdapter, grpcEncoding)
      responseReader.use {
        while (true) {
          val message = it.readMessage() ?: break
          responseChannel.send(message)
        }

        val trailers = response.trailers()
        if (!method.responseStreaming) {
          responseChannel.close()
          return@launch
        }

        val grpcStatus = trailers.get("grpc-status")
        when (grpcStatus) {
          "0" -> {
            responseChannel.close()
          }
          else -> {
            responseChannel.close(IOException("unexpected or absent grpc-status: $grpcStatus"))
          }
        }
      }
    }

    return requestChannel
  }
}