package misk.cloud.gcp.testing

import com.google.api.client.http.LowLevelHttpRequest
import com.google.api.client.json.jackson2.JacksonFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class FakeHttpRequest constructor(
  val method: String,
  val url: String,
  private val router: (FakeHttpRequest) -> FakeHttpResponse
) : LowLevelHttpRequest() {
  private val headers = LinkedHashMap<String, String>()
  private var contentBytes: ByteArray? = null

  override fun execute() = router.invoke(this)

  override fun addHeader(name: String, value: String) {
    headers.put(name, value)
  }

  fun header(name: String): String? = headers[name]

  val content: ByteArray?
    get() {
      if (streamingContent == null) {
        return null
      }

      if (contentBytes == null) {
        val os = ByteArrayOutputStream()
        streamingContent.writeTo(os)
        contentBytes = os.toByteArray()
      }

      return contentBytes
    }

  val stringContent: String? get() = content?.toString(Charsets.UTF_8)

  inline fun <reified T : Any> jsonContent(): T? = content.let {
    JacksonFactory().fromInputStream(ByteArrayInputStream(it), T::class.java)
  }
}
