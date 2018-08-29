package misk.moshi

import com.squareup.moshi.Moshi
import misk.MiskServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = false)
internal class ByteStringAdapterTest {
  @MiskTestModule
  val module = MiskServiceModule()

  @Inject
  lateinit var moshi: Moshi

  @Test
  fun encodeAndDecode() {
    val json = """
        |{
        |  "a": "AP8=",
        |  "b": "_wA="
        |}
        |""".trimMargin()
    val value = ByteStringPair("00ff".decodeHex(), "ff00".decodeHex())
    val jsonAdapter = moshi.adapter<ByteStringPair>().indent("  ")
    assertThat(jsonAdapter.toJson(value)).isEqualTo(json.trimMargin())
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(value)
  }

  @Test
  fun nulls() {
    val json = """
        |{
        |  "a": null,
        |  "b": null
        |}
        |""".trimMargin()
    val value = ByteStringPair(null, null)
    val jsonAdapter = moshi.adapter<ByteStringPair>().indent("  ").serializeNulls()
    assertThat(jsonAdapter.toJson(value)).isEqualTo(json.trimMargin())
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(value)
  }

  data class ByteStringPair(val a: ByteString?, val b: ByteString?)
}
