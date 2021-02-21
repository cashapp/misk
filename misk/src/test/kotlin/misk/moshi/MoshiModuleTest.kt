package misk.moshi

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import javax.inject.Inject

@MiskTest(startService = false)
internal class MoshiModuleTest {
  @MiskTestModule
  val module = TestModule()

  @Inject
  lateinit var moshi: Moshi

  /** Ensure we have Moshi's Kotlin features, including named and default parameters. */
  @Test
  fun kotlinFeaturesAreEnabled() {
    val jsonAdapter = moshi.adapter<Movie>()
    val value = Movie("Star Wars", release_year = null)
    assertThat(jsonAdapter.fromJson("""{"name":"Star Wars"}""")).isEqualTo(value)
    assertThat(jsonAdapter.toJson(value)).isEqualTo("""{"name":"Star Wars","genre":"sci-fi"}""")
  }

  /** But if we install a custom adapter, it takes precedence. */
  @Test
  fun customAdapterIsPreferred() {
    val json = """[3,5]""".trimMargin()
    val value = Point(3, 5)
    val jsonAdapter = moshi.adapter<Point>()
    assertThat(jsonAdapter.toJson(value)).isEqualTo(json.trimMargin())
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(value)
  }

  /** We can also install an adapter that has an explicit type. */
  @Test
  fun jsonAdapterWithExplicitType() {
    val json = "5.5"
    val value = Hat(5.5)
    val jsonAdapter = moshi.adapter<Hat>()
    assertThat(jsonAdapter.toJson(value)).isEqualTo(json.trimMargin())
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(value)
  }

  @Test
  fun `BigDecimal adapter converts from and to json`() {
    val json = "\"5.5\""
    val value = BigDecimal("5.5")
    val jsonAdapter = moshi.adapter<BigDecimal>()
    assertThat(jsonAdapter.toJson(value)).isEqualTo(json)
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(value)
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(
        MoshiAdapterModule(object : Any() {
          @ToJson fun pointToJson(point: Point) = listOf(point.x, point.y)
          @FromJson fun jsonToPoint(pair: List<Int>) = Point(pair[0], pair[1])
        })
      )
      install(
        MoshiAdapterModule<Hat>(object : JsonAdapter<Hat>() {
          override fun fromJson(reader: JsonReader): Hat? {
            return Hat(reader.nextDouble())
          }

          override fun toJson(writer: JsonWriter, value: Hat?) {
            writer.value(value!!.size)
          }
        })
      )
    }
  }

  data class Point(
    val x: Int,
    val y: Int
  )

  data class Movie(
    val name: String,
    val release_year: Int?,
    val genre: String = "sci-fi"
  )

  data class Hat(val size: Double)
}
