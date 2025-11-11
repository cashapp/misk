package misk.moshi.time

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import misk.MiskTestingServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

@MiskTest(startService = false)
internal class DurationAdapterTest {
  @MiskTestModule
  val module = MiskTestingServiceModule()

  private val moshi: Moshi = Moshi.Builder().add(DurationAdapter).add(KotlinJsonAdapterFactory()).build()

  @Test
  fun encodeAndDecodeDuration() {
    val jsonAdapter = moshi.adapter(Duration::class.java).indent("  ")
    val json = "\"PT1H30M45S\""
    val value = Duration.ofHours(1).plusMinutes(30).plusSeconds(45)
    assertThat(jsonAdapter.toJson(value)).isEqualTo(json)
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(value)
  }

  @Test
  fun nullDuration() {
    val jsonAdapter = moshi.adapter(Duration::class.java).indent("  ")
    val json = "null"
    val value: Duration? = null
    assertThat(jsonAdapter.toJson(value)).isEqualTo(json)
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(value)
  }

  @Test
  fun encodeAndDecodeZeroDuration() {
    val jsonAdapter = moshi.adapter(Duration::class.java).indent("  ")
    val json = "\"PT0S\""
    val value = Duration.ZERO
    assertThat(jsonAdapter.toJson(value)).isEqualTo(json)
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(value)
  }

  @Test
  fun encodeAndDecodeNegativeDuration() {
    val jsonAdapter = moshi.adapter(Duration::class.java).indent("  ")
    val json = "\"PT-10S\""
    val value = Duration.ofSeconds(-10)
    assertThat(jsonAdapter.toJson(value)).isEqualTo(json)
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(value)
  }

  @Test
  fun encodeAndDecodeMilliseconds() {
    val jsonAdapter = moshi.adapter(Duration::class.java).indent("  ")
    val json = "\"PT0.123S\""
    val value = Duration.ofMillis(123)
    assertThat(jsonAdapter.toJson(value)).isEqualTo(json)
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(value)
  }

  @Test
  fun encodeAndDecodeDays() {
    val jsonAdapter = moshi.adapter(Duration::class.java).indent("  ")
    val json = "\"PT51H\""
    val value = Duration.ofDays(2).plusHours(3)
    assertThat(jsonAdapter.toJson(value)).isEqualTo(json)
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(value)
  }

  @Test
  fun encodeAndDecodeWithDataClass() {
    val json = """
        |{
        |  "timeout": "PT30S",
        |  "retryDelay": "PT5S"
        |}""".trimMargin()
    val value = DurationPair(Duration.ofSeconds(30), Duration.ofSeconds(5))
    val jsonAdapter = moshi.adapter(DurationPair::class.java).indent("  ")
    assertThat(jsonAdapter.toJson(value)).isEqualTo(json)
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(value)
  }

  @Test
  fun nullDurationInDataClass() {
    val json = """
        |{
        |  "timeout": null,
        |  "retryDelay": null
        |}""".trimMargin()
    val value = DurationPair(null, null)
    val jsonAdapter = moshi.adapter(DurationPair::class.java).indent("  ").serializeNulls()
    assertThat(jsonAdapter.toJson(value)).isEqualTo(json)
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(value)
  }

  data class DurationPair(val timeout: Duration?, val retryDelay: Duration?)
}