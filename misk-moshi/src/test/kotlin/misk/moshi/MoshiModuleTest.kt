package misk.moshi

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.protos.test.grpc.HelloRequest
import helpers.protos.Dinosaur
import jakarta.inject.Inject
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = false)
internal class MoshiModuleTest {
  @MiskTestModule val module = TestModule()

  @Inject lateinit var moshi: Moshi

  /**
   * This test verifies that use of MoshiAdapterModule.addLast works as expected. See also
   * https://github.com/square/moshi#precedence
   */
  @Test
  fun addAdaptersPreferredOverAddLastAdapters() {

    // A generic dog is serialized by the Dog adapter, which was added via addLast()
    val dogAdapter = moshi.adapter<Dog>()
    val maxTheDog = Dog("Max")
    assertThat(dogAdapter.fromJson("""["Max, who is a dog"]""")!!.name).isEqualTo(maxTheDog.name)
    assertThat(dogAdapter.toJson(maxTheDog)).isEqualTo("""["Max, who is a dog"]""")

    // But a Golden gets serialized by the GoldenRetriever adapter, which was added via add(),
    // even though that add() call happened after the Dog adapter's addLast() call
    val goldenAdapter = moshi.adapter<GoldenRetriever>()
    val cooperTheGolden = GoldenRetriever("Cooper")
    assertThat(goldenAdapter.fromJson("""["Cooper, who is SUCH a good boye!"]""")!!.name)
      .isEqualTo(cooperTheGolden.name)
    assertThat(goldenAdapter.toJson(cooperTheGolden)).isEqualTo("""["Cooper, who is SUCH a good boye!"]""")
  }

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

  /** Ensure we get our custom Wire Message features, including ignoring unknown fields */
  @Test
  fun wireFeaturesAreEnabled() {
    val jsonAdapter = moshi.adapter<HelloRequest>()
    val value = HelloRequest.Builder().name("Robbie").build()
    assertThat(jsonAdapter.fromJson("""{"name":"Robbie"}""")).isEqualTo(value)
    assertThat(jsonAdapter.toJson(value)).isEqualTo("""{"name":"Robbie"}""")
  }

  /** But if we install a custom adapter for a Wire class, it takes precedence. */
  @Test
  fun customAdapterIsPreferredOverWireAdapter() {
    val jsonAdapter = moshi.adapter<Dinosaur>()
    val value = Dinosaur.Builder().name("Robbie").build()
    assertThat(jsonAdapter.fromJson("""["Rawr! My name is Robbie"]""")).isEqualTo(value)
    assertThat(jsonAdapter.toJson(value)).isEqualTo("""["Rawr! My name is Robbie"]""")
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

  @Test
  fun `OffsetDateTime adapter converts from and to json`() {
    val json = "\"0001-01-01T01:01:00Z\""
    val value = OffsetDateTime.of(LocalDateTime.of(1, 1, 1, 1, 1), ZoneOffset.UTC)
    val jsonAdapter = moshi.adapter<OffsetDateTime>()
    assertThat(jsonAdapter.toJson(value)).isEqualTo(json)
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(value)
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(
        MoshiAdapterModule(
          object : Any() {
            @ToJson fun pointToJson(point: Point) = listOf(point.x, point.y)

            @FromJson fun jsonToPoint(pair: List<Int>) = Point(pair[0], pair[1])
          }
        )
      )
      install(
        MoshiAdapterModule<Hat>(
          object : JsonAdapter<Hat>() {
            override fun fromJson(reader: JsonReader): Hat? {
              return Hat(reader.nextDouble())
            }

            override fun toJson(writer: JsonWriter, value: Hat?) {
              writer.value(value!!.size)
            }
          }
        )
      )
      install(
        MoshiAdapterModule<Dinosaur>(
          object : JsonAdapter<Dinosaur>() {
            override fun fromJson(reader: JsonReader): Dinosaur? {
              reader.beginArray()
              val name = reader.nextString().split(" ").last()
              reader.endArray()
              return Dinosaur.Builder().name(name).build()
            }

            override fun toJson(writer: JsonWriter, value: Dinosaur?) {
              writer.beginArray()
              writer.value("Rawr! My name is ${value?.name}")
              writer.endArray()
            }
          }
        )
      )
      install(
        MoshiAdapterModule<Dog>(
          object : JsonAdapter<Dog>() {
            override fun fromJson(reader: JsonReader): Dog? {
              reader.beginArray()
              val name = reader.nextString().split(",").first()
              reader.endArray()
              return Dog(name)
            }

            override fun toJson(writer: JsonWriter, value: Dog?) {
              writer.beginArray()
              writer.value("${value?.name}, who is a dog")
              writer.endArray()
            }
          },
          addLast = true,
        )
      )
      install(
        MoshiAdapterModule<GoldenRetriever>(
          object : JsonAdapter<GoldenRetriever>() {
            override fun fromJson(reader: JsonReader): GoldenRetriever? {
              reader.beginArray()
              val name = reader.nextString().split(",").first()
              reader.endArray()
              return GoldenRetriever(name)
            }

            override fun toJson(writer: JsonWriter, value: GoldenRetriever?) {
              writer.beginArray()
              writer.value("${value?.name}, who is SUCH a good boye!")
              writer.endArray()
            }
          }
        )
      )
    }
  }

  data class Point(val x: Int, val y: Int)

  data class Movie(val name: String, val release_year: Int?, val genre: String = "sci-fi")

  data class Hat(val size: Double)

  open class Dog(val name: String)

  class GoldenRetriever(name: String) : Dog(name)
}
