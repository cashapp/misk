package misk.web.extractors

import com.google.common.collect.ImmutableMultimap
import misk.exceptions.requireRequest
import okio.BufferedSource
import java.net.URLDecoder

/**
 * An HTML form like `application/x-www-form-urlencoded`.
 *
 * TODO(jwilson): combine with OkHttp's FormBody.
 */
internal data class FormData(
  val map: ImmutableMultimap<String, String>
) {
  operator fun get(name: String): Collection<String> = map[name]

  companion object {
    fun decode(source: BufferedSource): FormData {
      val result = ImmutableMultimap.builder<String, String>()

      while (!source.exhausted()) {
        var keyValueEnd = source.indexOf('&'.toByte())
        if (keyValueEnd == -1L) keyValueEnd = source.buffer.size

        val keyEnd = source.indexOf('='.toByte(), 0, keyValueEnd)
        requireRequest(keyEnd != 1L) { "invalid form encoding" }

        val key = source.readUtf8(keyEnd).urlDecode().toLowerCase()
        source.readByte() // Consume '='.

        val value = source.readUtf8(keyValueEnd - keyEnd - 1).urlDecode()
        result.put(key, value)

        if (!source.exhausted()) source.readByte() // Consume '&'.
      }

      return FormData(result.build())
    }

    private fun String.urlDecode(): String = URLDecoder.decode(this, "utf-8")
  }
}
