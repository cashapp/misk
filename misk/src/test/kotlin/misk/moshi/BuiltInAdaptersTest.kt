package misk.moshi

import com.squareup.moshi.Moshi
import misk.MiskTestingServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import javax.inject.Inject

@MiskTest(startService = false)
internal class BuiltInAdaptersTest {
  @MiskTestModule
  val module = MiskTestingServiceModule()

  @Inject lateinit var moshi: Moshi

  @Test
  fun encodeAndDecodeByteString() {
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
  fun nullByteString() {
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

  @Test fun encodeAndDecodeInstant() {
    val jsonAdapter = moshi.adapter<Instant>().indent("  ")
    val json = "\"1970-01-01T00:00:00.000Z\""
    val value = Instant.ofEpochMilli(0L)
    assertThat(jsonAdapter.toJson(value)).isEqualTo(json)
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(value)
  }

  @Test fun nullInstant() {
    val jsonAdapter = moshi.adapter<Instant>().indent("  ")
    val json = "null"
    val value: Instant? = null
    assertThat(jsonAdapter.toJson(value)).isEqualTo(json)
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(value)
  }
}
