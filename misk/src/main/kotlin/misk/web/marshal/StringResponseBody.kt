package misk.web.marshal

import misk.web.ResponseBody
import okio.BufferedSink

/** A [ResponseBody] that writes the value out as a string */
class StringResponseBody(val obj: Any) : ResponseBody {
  override fun writeTo(sink: BufferedSink) {
    sink.writeUtf8(obj.toString())
  }
}
