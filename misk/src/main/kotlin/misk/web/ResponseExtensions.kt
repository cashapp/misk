package misk.web

import okio.Buffer
import okio.BufferedSink
import okio.ByteString

/** Returns a [ResponseBody] that writes this out as UTF-8. */
fun String.toResponseBody(): ResponseBody {
  return object : ResponseBody {
    override fun writeTo(sink: BufferedSink) {
      sink.writeUtf8(this@toResponseBody)
    }
  }
}

/** Returns a [ResponseBody] that writes this out as bytestring. */
fun ByteString.toResponseBody(): ResponseBody {
  return object : ResponseBody {
    override fun writeTo(sink: BufferedSink) {
      sink.write(this@toResponseBody)
    }
  }
}

fun Response<*>.readUtf8(): String {
  val buffer = Buffer()
  (body as ResponseBody).writeTo(buffer)
  return buffer.readUtf8()
}

fun okhttp3.Response.toMisk(): Response<ResponseBody> {
  val miskBody = if (body is okhttp3.ResponseBody) {
    object : ResponseBody {
      override fun writeTo(sink: BufferedSink) {
        body!!.use {
          sink.writeAll(it.source())
        }
      }
    }
  } else "".toResponseBody()
  return Response(miskBody, headers, code)
}
